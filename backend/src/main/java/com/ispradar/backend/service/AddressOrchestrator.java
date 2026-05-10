package com.ispradar.backend.service;

import com.ispradar.backend.dto.AddressItem;
import com.ispradar.backend.dto.AddressResponse;
import com.ispradar.backend.dto.AvailabilityRequest;
import com.ispradar.backend.dto.AvailabilityResponse;
import com.ispradar.backend.dto.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class AddressOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(AddressOrchestrator.class);

    private final CosmoteService cosmote;
    private final VodafoneService vodafone;
    private final NovaService nova;
    private final ExecutorService executor;

    public AddressOrchestrator(CosmoteService cosmote, VodafoneService vodafone, NovaService nova, ExecutorService ispExecutor) {
        this.cosmote = cosmote;
        this.vodafone = vodafone;
        this.nova = nova;
        this.executor = ispExecutor;
    }

    public AddressResponse getStates() {
        var c = runAsync(cosmote::fetchStates);
        var v = runAsync(vodafone::fetchStates);
        var n = runAsync(nova::fetchStates);
        return merge(await(c), await(v), await(n));
    }

    public AddressResponse getMunicipalities(AddressItem state) {
        var c = state != null && state.cosmoteCtx() != null
                ? runAsync(() -> cosmote.fetchMunicipalities(state.cosmoteCtx()))
                : done();
        var v = state != null && state.vodafoneCtx() != null
                ? runAsync(() -> vodafone.fetchCities(state.vodafoneCtx()))
                : done();
        var n = state != null && state.novaCtx() != null
                ? runAsync(() -> nova.fetchMunicipalities(state.novaCtx())) : done();
        return merge(await(c), await(v), await(n));
    }

    public AddressResponse getPostalCodes(AddressItem state, AddressItem municipality) {
        if (state == null || municipality == null
                || state.vodafoneCtx() == null || municipality.vodafoneCtx() == null) {
            return empty();
        }
        var v = runAsync(() -> vodafone.fetchPostalCodes(state.vodafoneCtx(), municipality.vodafoneCtx()));
        return merge(Map.of(), await(v), Map.of());
    }

    public AddressResponse getStreets(AddressItem state, AddressItem municipality, AddressItem postalCode) {
        var c = municipality != null && municipality.cosmoteCtx() != null
                ? runAsync(() -> cosmote.fetchStreets(municipality.cosmoteCtx()))
                : done();
        Map<String, Object> postalCtx = vodafoneCtxFrom(postalCode);
        var v = state != null && municipality != null && postalCtx != null
            && state.vodafoneCtx() != null && municipality.vodafoneCtx() != null
            ? runAsync(() -> vodafone.fetchStreets(state.vodafoneCtx(), municipality.vodafoneCtx(), postalCtx))
                : done();
        var n = state != null && municipality != null 
                && state.novaCtx() != null && municipality.novaCtx() != null
                ? runAsync(() -> nova.fetchStreets(state.novaCtx(), municipality.novaCtx())) : done();
                
        return merge(await(c), await(v), await(n));
    }

    public AddressResponse getAreas(AddressItem street) {
        if (street == null || street.cosmoteCtx() == null) return empty();
        var c = runAsync(() -> cosmote.fetchAreas(street.cosmoteCtx()));
        return merge(await(c), Map.of(), Map.of());
    }

    public AddressResponse getNumbers(AddressItem state, AddressItem municipality,
                                      AddressItem postalCode, AddressItem street) {
        if (state == null || municipality == null || postalCode == null || street == null
                || state.vodafoneCtx() == null || municipality.vodafoneCtx() == null
            || street.vodafoneCtx() == null) {
            return empty();
        }
        Map<String, Object> postalCtx = vodafoneCtxFrom(postalCode);
        if (postalCtx == null) return empty();
        var v = runAsync(() -> vodafone.fetchNumbers(
            state.vodafoneCtx(), municipality.vodafoneCtx(),
            postalCtx, street.vodafoneCtx()));
        return merge(Map.of(), await(v), Map.of());
    }

    public AvailabilityResponse checkAvailability(AvailabilityRequest req) {
        List<Plan> plans = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (req != null
                && req.state() != null
                && req.municipality() != null
                && req.area() != null
                && req.street() != null
                && req.number() != null && !req.number().isBlank()
                && req.state().cosmoteCtx() != null
                && req.municipality().cosmoteCtx() != null
                && req.area().cosmoteCtx() != null
                && req.street().cosmoteCtx() != null) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    plans.addAll(cosmote.checkAvailability(
                            req.state().cosmoteCtx(),
                            req.municipality().cosmoteCtx(),
                            req.area().cosmoteCtx(),
                            req.street().cosmoteCtx(),
                            req.number()));
                } catch (Exception e) {
                    LOG.error("[Cosmote] check failed", e);
                    errors.add("COSMOTE: " + e.getMessage());
                }
            }, executor));
        }

        if (req != null
                && req.state() != null
                && req.municipality() != null
                && req.postalCode() != null
                && req.street() != null
                && req.state().vodafoneCtx() != null
                && req.municipality().vodafoneCtx() != null
                && req.street().vodafoneCtx() != null) {
            Map<String, Object> postalCtx = vodafoneCtxFrom(req.postalCode());
            Map<String, Object> tmpNumberCtx = vodafoneCtxFrom(req.numberItem());
            if (tmpNumberCtx == null && req.number() != null && !req.number().isBlank()) {
                tmpNumberCtx = Map.of("label", req.number(), "value", req.number());
            }
            if (postalCtx != null && tmpNumberCtx != null) {
                final Map<String, Object> numberCtx = tmpNumberCtx;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    plans.addAll(vodafone.checkAvailability(
                            req.state().vodafoneCtx(),
                            req.municipality().vodafoneCtx(),
                            postalCtx,
                            req.street().vodafoneCtx(),
                            numberCtx));
                } catch (Exception e) {
                    LOG.error("[Vodafone] check failed", e);
                    errors.add("VODAFONE: " + e.getMessage());
                }
            }, executor));
            }
        }

        if (req != null 
                && req.state() != null 
                && req.municipality() != null 
                && req.street() != null
                && req.number() != null 
                && !req.number().isBlank()
                && req.state().novaCtx() != null
                && req.municipality().novaCtx() != null
                && req.street().novaCtx() != null) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    plans.addAll(nova.checkAvailability(
                            req.state().novaCtx(), req.municipality().novaCtx(),
                            req.street().novaCtx(), req.number()));
                } catch (Exception e) {
                    LOG.error("[Nova] check failed", e);
                    errors.add("NOVA: " + e.getMessage());
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return new AvailabilityResponse(buildAddressLabel(req), new ArrayList<>(plans), new ArrayList<>(errors));
    }

    private AddressResponse merge(
            Map<String, Map<String, Object>> cosmoteMap,
            Map<String, Map<String, Object>> vodafoneMap,
            Map<String, Map<String, Object>> novaMap) {

        Map<String, Map<String, Object>> vodafoneNorm = new LinkedHashMap<>();
        vodafoneMap.forEach((label, ctx) -> vodafoneNorm.put(normalize(label), ctx));

        Map<String, Map<String, Object>> novaNorm = new LinkedHashMap<>();
        novaMap.forEach((label, ctx) -> novaNorm.put(normalize(label), ctx));

        Map<String, AddressItem> merged = new LinkedHashMap<>();
        cosmoteMap.forEach((label, cCtx) -> {
            String key = normalize(label);
            Map<String, Object> vCtx = vodafoneNorm.get(key);
            Map<String, Object> nCtx = novaNorm.get(key);
            merged.put(key, new AddressItem(label, cCtx, vCtx, nCtx));
        });

        vodafoneMap.forEach((label, vCtx) -> {
            String key = normalize(label);
            if (!merged.containsKey(key)) {
                merged.put(key, new AddressItem(label, null, vCtx, novaNorm.get(key)));
            }
        });

        novaMap.forEach((label, nCtx) -> {
            String key = normalize(label);
            if (!merged.containsKey(key)) {
                merged.put(key, new AddressItem(label, null, null, nCtx));
            }
        });

        List<AddressItem> sorted = merged.values().stream()
                .sorted(Comparator.comparing(AddressItem::label))
                .toList();

        return new AddressResponse(sorted);
    }

    private static String normalize(String s) {
        String up = s.toUpperCase(Locale.forLanguageTag("el")).strip();
        String noPrefix = up.replaceFirst("^(Ν\\.|Ν)\\s+", "")
            .replaceFirst("^(Δ\\.|Δ)\\s+", "");
        String noParens = noPrefix.replaceAll("\\s*\\([^)]*\\)", "");
        String nfd = Normalizer.normalize(noParens, Normalizer.Form.NFD);
        String noTones = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        return noTones.replaceAll("\\s+", " ").strip();
    }

    private CompletableFuture<Map<String, Map<String, Object>>> runAsync(CheckedSupplier supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                LOG.error("Provider fetch failed", e);
                return Map.of();
            }
        }, executor);
    }

    private CompletableFuture<Map<String, Map<String, Object>>> done() {
        return CompletableFuture.completedFuture(Map.of());
    }

    private Map<String, Map<String, Object>> await(CompletableFuture<Map<String, Map<String, Object>>> f) {
        return f.join();
    }

    private AddressResponse empty() {
        return new AddressResponse(List.of());
    }

    private Map<String, Object> vodafoneCtxFrom(AddressItem item) {
        if (item == null) return null;
        if (item.vodafoneCtx() != null) return item.vodafoneCtx();
        if (item.label() == null || item.label().isBlank()) return null;
        return Map.of("label", item.label(), "value", item.label());
    }

    private String buildAddressLabel(AvailabilityRequest req) {
        if (req == null) return "";
        String street = req.street() != null ? req.street().label() : "";
        String num = req.number() != null && !req.number().isBlank()
                ? req.number()
                : (req.numberItem() != null ? req.numberItem().label() : "");
        String city = req.municipality() != null ? req.municipality().label() : "";
        return (street + " " + num + ", " + city).strip();
    }

    @FunctionalInterface
    interface CheckedSupplier {
        Map<String, Map<String, Object>> get() throws Exception;
    }
}