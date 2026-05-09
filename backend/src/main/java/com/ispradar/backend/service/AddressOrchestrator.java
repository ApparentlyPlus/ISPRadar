package com.ispradar.backend.service;

import com.ispradar.backend.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Service
public class AddressOrchestrator {

    private final CosmoteService   cosmote;
    private final VodafoneService  vodafone;
    private final ExecutorService  executor;

    public AddressOrchestrator(CosmoteService cosmote,
                               VodafoneService vodafone,
                               ExecutorService ispExecutor) {
        this.cosmote  = cosmote;
        this.vodafone = vodafone;
        this.executor = ispExecutor;
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    public AddressResponse getStates() {
        var c = runAsync(cosmote::fetchStates);
        var v = runAsync(vodafone::fetchStates);
        return merge(await(c), await(v));
    }

    public AddressResponse getMunicipalities(AddressItem state) {
        var c = state.cosmoteCtx()  != null
                ? runAsync(() -> cosmote.fetchMunicipalities(state.cosmoteCtx()))
                : done();
        var v = state.vodafoneCtx() != null
                ? runAsync(() -> vodafone.fetchCities(state.vodafoneCtx()))
                : done();
        return merge(await(c), await(v));
    }

    /**
     * Vodafone requires postal code; Cosmote does not.
     * The frontend calls this after municipality and uses the results
     * to populate the postal code dropdown (Vodafone only).
     */
    public AddressResponse getPostalCodes(AddressItem state, AddressItem municipality) {
        if (state.vodafoneCtx() == null || municipality.vodafoneCtx() == null)
            return empty();
        var v = runAsync(() -> vodafone.fetchPostalCodes(state.vodafoneCtx(), municipality.vodafoneCtx()));
        return merge(Map.of(), await(v));
    }

    /**
     * Streets — both providers queried in parallel.
     * postalCode may be null if Vodafone data is unavailable at this address.
     */
    public AddressResponse getStreets(AddressItem state, AddressItem municipality, AddressItem postalCode) {
        var c = municipality.cosmoteCtx() != null
                ? runAsync(() -> cosmote.fetchStreets(municipality.cosmoteCtx()))
                : done();
        var v = (state.vodafoneCtx() != null && municipality.vodafoneCtx() != null && postalCode != null)
                ? runAsync(() -> vodafone.fetchStreets(state.vodafoneCtx(), municipality.vodafoneCtx(), postalCode.vodafoneCtx()))
                : done();
        return merge(await(c), await(v));
    }

    /** Areas — Cosmote only. */
    public AddressResponse getAreas(AddressItem street) {
        if (street.cosmoteCtx() == null) return empty();
        var c = runAsync(() -> cosmote.fetchAreas(street.cosmoteCtx()));
        return merge(await(c), Map.of());
    }

    /** Street numbers — Vodafone only (Cosmote uses free-text). */
    public AddressResponse getNumbers(AddressItem state, AddressItem municipality,
                                      AddressItem postalCode, AddressItem street) {
        if (state.vodafoneCtx() == null || municipality.vodafoneCtx() == null
                || postalCode == null || street.vodafoneCtx() == null)
            return empty();
        var v = runAsync(() -> vodafone.fetchNumbers(
                state.vodafoneCtx(), municipality.vodafoneCtx(),
                postalCode.vodafoneCtx(), street.vodafoneCtx()));
        return merge(Map.of(), await(v));
    }

    /** Final check — both providers run in parallel; partial failures are non-fatal. */
    public AvailabilityResponse checkAvailability(AvailabilityRequest req) {
        List<Plan>   plans  = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // ── Cosmote ──────────────────────────────────────────────────────────
        if (req.state().cosmoteCtx() != null
                && req.area() != null
                && req.street().cosmoteCtx() != null
                && req.number() != null && !req.number().isBlank()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    plans.addAll(cosmote.checkAvailability(
                            req.state().cosmoteCtx(),
                            req.municipality().cosmoteCtx(),
                            req.area().cosmoteCtx(),
                            req.street().cosmoteCtx(),
                            req.number()));
                } catch (Exception e) {
                    log.error("[Cosmote] check failed", e);
                    errors.add("COSMOTE: " + e.getMessage());
                }
            }, executor));
        }

        // ── Vodafone ─────────────────────────────────────────────────────────
        if (req.state().vodafoneCtx() != null
                && req.postalCode() != null
                && req.street().vodafoneCtx() != null
                && req.numberItem() != null
                && req.numberItem().vodafoneCtx() != null) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    plans.addAll(vodafone.checkAvailability(
                            req.state().vodafoneCtx(),
                            req.municipality().vodafoneCtx(),
                            req.postalCode().vodafoneCtx(),
                            req.street().vodafoneCtx(),
                            req.numberItem().vodafoneCtx()));
                } catch (Exception e) {
                    log.error("[Vodafone] check failed", e);
                    errors.add("VODAFONE: " + e.getMessage());
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        String address = buildAddressLabel(req);
        return new AvailabilityResponse(address, new ArrayList<>(plans), new ArrayList<>(errors));
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    /**
     * Deduplicates Cosmote + Vodafone results by normalised label.
     * Items present in both providers carry both context blobs.
     * Results are sorted alphabetically.
     */
    private AddressResponse merge(
            Map<String, Map<String, Object>> cosmoteMap,
            Map<String, Map<String, Object>> vodafoneMap) {

        // Precompute normalised keys for Vodafone map
        Map<String, Map<String, Object>> vodafoneNorm = new LinkedHashMap<>();
        vodafoneMap.forEach((label, ctx) -> vodafoneNorm.put(normalize(label), ctx));

        Map<String, AddressItem> merged = new LinkedHashMap<>();

        // Seed with Cosmote entries, attach Vodafone ctx if label matches
        cosmoteMap.forEach((label, cCtx) -> {
            String key    = normalize(label);
            Map<String, Object> vCtx = vodafoneNorm.get(key);
            merged.put(key, new AddressItem(label, cCtx, vCtx));
        });

        // Add Vodafone-only entries (not already seeded from Cosmote)
        vodafoneMap.forEach((label, vCtx) -> {
            String key = normalize(label);
            if (!merged.containsKey(key))
                merged.put(key, new AddressItem(label, null, vCtx));
        });

        List<AddressItem> sorted = merged.values().stream()
                .sorted(Comparator.comparing(AddressItem::label))
                .toList();

        return new AddressResponse(sorted);
    }

    /**
     * Greek-aware normalisation: uppercase + strip tonos.
     * Handles "ΑΘΉΝΑ" == "ΑΘΗΝΑ", "Αθήνα" == "ΑΘΗΝΑ", etc.
     */
    private static String normalize(String s) {
        String up  = s.toUpperCase(java.util.Locale.forLanguageTag("el")).strip();
        String nfd = Normalizer.normalize(up, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <T> CompletableFuture<Map<String, Map<String, Object>>> runAsync(
            CheckedSupplier<Map<String, Map<String, Object>>> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (Exception e) {
                log.error("Provider fetch failed", e);
                return Map.of();   // graceful degradation
            }
        }, executor);
    }

    private CompletableFuture<Map<String, Map<String, Object>>> done() {
        return CompletableFuture.completedFuture(Map.of());
    }

    private Map<String, Map<String, Object>> await(CompletableFuture<Map<String, Map<String, Object>>> f) {
        return f.join();
    }

    private AddressResponse empty() { return new AddressResponse(List.of()); }

    private String buildAddressLabel(AvailabilityRequest req) {
        String street = req.street()  != null ? req.street().label()       : "";
        String num    = req.number()  != null && !req.number().isBlank()
                        ? req.number()
                        : (req.numberItem() != null ? req.numberItem().label() : "");
        String city   = req.municipality() != null ? req.municipality().label() : "";
        return (street + " " + num + ", " + city).strip();
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}