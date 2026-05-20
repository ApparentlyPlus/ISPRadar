package com.ispradar.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispradar.backend.dto.Plan;
import com.ispradar.backend.service.PlanCatalog.PlanMetadata;
import com.ispradar.backend.util.http.BaseIspHttpClient;
import com.ispradar.backend.util.http.HttpStatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NovaService extends BaseIspHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(NovaService.class);

    private static final String INIT_URL = "https://nova.gr/statheri-tilefonia/programmata/stathero-internet";
    private static final String REGIONS_API = "https://nova.gr/api/address/regions";
    private static final String MUNICIPALITIES_API = "https://nova.gr/api/address/municipalities";
    private static final String STREETS_API = "https://nova.gr/api/address/streets/";
    private static final String AVAIL_API = "https://nova.gr/api/GetEligibilityInfo";
    
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
    private static final List<String> GREEK_ALPHABET = List.of(
            "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ", 
            "Ν", "Ξ", "Ο", "Π", "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
    );

    private final ExecutorService executorService;

    public NovaService(ObjectMapper objectMapper, ExecutorService executorService) {
        super(objectMapper);
        this.executorService = executorService;
    }

    @Override
    protected String getProviderName() { return "Nova"; }

    @Override
    protected void initializeSession() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(INIT_URL))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> r = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (HttpStatusCode.isError(r.statusCode())) 
            throw new IOException("Session init failed: HTTP " + r.statusCode());
    }

    private HttpRequest.Builder buildApiReq(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "el")
                .header("Priority", "u=1, i")
                .header("Referer", INIT_URL)
                .header("sec-ch-ua", "\"Google Chrome\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .header("X-Requested-With", "XMLHttpRequest");
    }

    public Map<String, Map<String, Object>> fetchStates() throws IOException, InterruptedException {
        HttpRequest req = buildApiReq(REGIONS_API).GET().build();
        HttpResponse<String> resp = executeWithRetry(req);

        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        List<String> results = extractResultsList(data);
        Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        
        for (String r : results)
            states.put(r, Map.of("region", r, "label", r));

        return states;
    }

    public Map<String, Map<String, Object>> fetchMunicipalities(Map<String, Object> stateCtx) throws IOException, InterruptedException {
        String region = encode((String) stateCtx.get("region"));
        HttpRequest req = buildApiReq(MUNICIPALITIES_API + "?region=" + region).GET().build();
        HttpResponse<String> resp = executeWithRetry(req);

        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        List<String> results = extractResultsList(data);

        Map<String, Map<String, Object>> municipalities = new LinkedHashMap<>();
        for (String r : results) {
            municipalities.put(r, Map.of(
                    "region", stateCtx.get("region"), 
                    "municipality", r, 
                    "label", r));
        }
        return municipalities;
    }

    public Map<String, Map<String, Object>> fetchStreets(Map<String, Object> stateCtx, Map<String, Object> munCtx) throws IOException, InterruptedException {
        String region = encode((String) stateCtx.get("region"));
        String municipality = encode((String) munCtx.get("municipality"));
        Map<String, Map<String, Object>> allStreets = Collections.synchronizedMap(new LinkedHashMap<>());

        var futures = GREEK_ALPHABET.stream().map(letter -> CompletableFuture.runAsync(() -> {
            try {
                String url = STREETS_API + encode(letter) + "?region=" + region + "&municipality=" + municipality;
                var req = buildApiReq(url).GET().build();
                var resp = executeWithRetry(req);
                extractResultsListOfMaps(resp.body()).forEach(s -> {
                    String streetName = (String) s.get("street");
                    Map<String, Object> ctx = new LinkedHashMap<>();
                    ctx.put("region", stateCtx.get("region"));
                    ctx.put("municipality", munCtx.get("municipality"));
                    ctx.put("city", s.get("city"));
                    ctx.put("street", streetName);
                    ctx.put("zipcode", s.get("zipcode"));
                    ctx.put("label", streetName);
                    allStreets.put(streetName, ctx);
                });
            } catch (Exception e) { LOG.error("[Nova] Street fetch error for letter " + letter, e); }
        }, executorService)).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
        return allStreets;
    }

    public List<Plan> checkAvailability(
            Map<String, Object> stateCtx,
            Map<String, Object> munCtx,
            Map<String, Object> streetCtx,
            String streetNumber) throws IOException, InterruptedException {
        Map<String, Object> payload = buildAvailabilityPayload(stateCtx, munCtx, streetCtx, streetNumber);
        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AVAIL_API))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "el")
                .header("Priority", "u=1, i")
                .header("Referer", "https://nova.gr/statheri-tilefonia/programmata/stathero-internet")
                .header("sec-ch-ua", "\"Google Chrome\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = executeWithRetry(req);
 
        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        return parseNovaPlans(data);
    }

    private Map<String, Object> buildAvailabilityPayload(
            Map<String, Object> stateCtx, Map<String, Object> munCtx, 
            Map<String, Object> streetCtx, String number) 
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        
        payload.put("packagePreselected", Map.of("code", "2P_FIBER_100", "title", "Fiber 100", "price", "29.0"));
        
        // Create a LinkedHashMap to safely store the null value for "price"
        Map<String, Object> packageSelected = new LinkedHashMap<>();
        packageSelected.put("code", "");
        packageSelected.put("title", "");
        packageSelected.put("price", null);
        packageSelected.put("packageGroupType", "");
        payload.put("packageSelected", packageSelected);
        payload.put("customerInfo", Map.of("isNewCustomer", true, "isExistingCustomerMoving", false, "landlineNumber", ""));
        payload.put("address", Map.of(
                "region", stateCtx.get("region"),
                "municipality", munCtx.get("municipality"),
                "city", streetCtx.get("city"),
                "street", streetCtx.get("street"),
                "zipcode", streetCtx.get("zipcode"),
                "streetNumber", number
        ));
        payload.put("userType", "Postpaid");
        payload.put("fixedPackagesType", "TwoP");
        payload.put("eligibleFixedPackagesType", null); 
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<Plan> parseNovaPlans(Map<String, Object> data) {
        List<Plan> plans = new ArrayList<>();
        Map<String, Object> result = (Map<String, Object>) data.getOrDefault("result", Map.of());
        
        // 1. Map actual speeds retrieved from the Eligibility API payloads
        Map<Double, Double> speedMap = new HashMap<>();
        List<Map<String, Object>> eligibilityResponse = 
                (List<Map<String, Object>>) result.getOrDefault("eligibilityResponse", List.of());
        
        if (eligibilityResponse != null) {
            for (Map<String, Object> er : eligibilityResponse) {
                // Priority 1: Explicit mapping from `profiles` array
                List<Map<String, Object>> profiles = 
                        (List<Map<String, Object>>) er.getOrDefault("profiles", List.of());
                if (profiles != null) {
                    for (Map<String, Object> profile : profiles) {
                        try {
                            Double dl = Double.parseDouble(String.valueOf(profile.get("maxDownloadSpeed")));
                            Double ul = Double.parseDouble(String.valueOf(profile.get("maxUploadSpeed")));
                            speedMap.put(dl, ul);
                        } catch (Exception e) {
                            // Ignored formatting issues
                        }
                    }
                }
                
                // Priority 2: Fallback for VDSL/ADSL using `profileTariffs` syntax (e.g. "50_5" or "24_1")
                List<Map<String, Object>> profileTariffs = 
                        (List<Map<String, Object>>) er.getOrDefault("profileTariffs", List.of());
                if (profileTariffs != null) {
                    for (Map<String, Object> pt : profileTariffs) {
                        try {
                            String profStr = (String) pt.get("profile");
                            if (profStr != null && profStr.contains("_")) {
                                String[] parts = profStr.split("_");
                                if (parts.length >= 2) {
                                    Double dl = Double.parseDouble(parts[0]);
                                    Double ul = Double.parseDouble(parts[1]);
                                    speedMap.putIfAbsent(dl, ul); // Don't override explicit profile mappings
                                }
                            }
                        } catch (Exception e) {
                            // Ignored formatting issues
                        }
                    }
                }
            }
        }

        // 2. Iterate through packages and apply mapped speeds
        List<Map<String, Object>> packages = (List<Map<String, Object>>) result.get("packages");
        if (packages != null) {
            Pattern speedPattern = Pattern.compile("(\\d+)");
            
            for (Map<String, Object> pkg : packages) {
                String title = (String) pkg.getOrDefault("title", "Unknown Package");
                Double maxDl = null;
                Double maxUl = null;
                
                Matcher m = speedPattern.matcher(title);
                while (m.find()) {
                    Double parsed = Double.parseDouble(m.group(1));
                    
                    // We check if this number exists in our parsed speed map 
                    // This prevents us from matching numbers like "2" from "2play"
                    if (speedMap.containsKey(parsed)) {
                        maxDl = parsed;
                        maxUl = speedMap.get(maxDl);
                    } 
                    // Fallback to the first reasonable internet speed number (>= 24) just in case
                    else if (maxDl == null && parsed >= 24) {
                        maxDl = parsed;
                    }
                }
                
                // Final fallback if the map didn't contain the upload speed
                    if (maxDl != null && maxUl == null) {
                        maxUl = maxDl / 10.0; // Typical rate
                    }

                    // If we couldn't determine a reasonable download speed, skip this package.
                    // This filters out mobile/5G offers (e.g. "5G Home Internet") and other non-fixed products
                    if (maxDl == null) {
                        LOG.debug("[Nova] Skipping package without parsed speed: {}", title);
                        continue;
                    }
                
                PlanMetadata meta = PlanCatalog.lookup("NOVA", title);
                String resolvedName = meta != null ? meta.name() : title;
                Double price = meta != null ? meta.price() : null;
                List<String> description = meta != null ? meta.description() : List.of();
                plans.add(new Plan("NOVA", resolvedName, maxDl, maxUl, price, description));
            }
        }
        return plans;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractResultsList(Map<String, Object> data) {
        return (List<String>) data.getOrDefault("result", Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResultsListOfMaps(String body) throws IOException {
        return (List<Map<String, Object>>)
                objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {})
                    .getOrDefault("result", List.of()); 
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}