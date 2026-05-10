package com.ispradar.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispradar.backend.dto.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
public class NovaService {

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

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    private static final class Session {
        private final CookieManager cookieManager;
        private final HttpClient httpClient;
        private boolean initialized;

        private Session(CookieManager cookieManager, HttpClient httpClient) {
            this.cookieManager = cookieManager;
            this.httpClient = httpClient;
            this.initialized = false;
        }
    }

    public NovaService(ObjectMapper objectMapper, ExecutorService executorService) {
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    private Session newSession() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        return new Session(cookieManager, httpClient);
    }

    private void init(Session session) throws IOException, InterruptedException {
        if (session.initialized) return;
        LOG.info("[Nova] Initialising session cookies...");
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(INIT_URL))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .GET()
                .build();
                
        HttpResponse<String> r = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) {
            throw new IOException("Nova session init failed: HTTP " + r.statusCode());
        }
        session.initialized = true;
    }

    private HttpRequest.Builder buildApiReq(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://www.vodafone.gr/statheri-internet-programmata")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin");
    }

    public Map<String, Map<String, Object>> fetchStates() throws IOException, InterruptedException {
        Session session = newSession();
        init(session);

        HttpRequest req = buildApiReq(REGIONS_API).GET().build();
        HttpResponse<String> resp = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() != 200) throw new IOException("Nova states error: " + resp.statusCode());

        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        List<String> results = extractResultsList(data);

        Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        for (String r : results) {
            states.put(r, Map.of("region", r, "label", r));
        }
        return states;
    }

    public Map<String, Map<String, Object>> fetchMunicipalities(Map<String, Object> stateCtx) throws IOException, InterruptedException {
        Session session = newSession();
        init(session);

        String region = encode((String) stateCtx.get("region"));
        HttpRequest req = buildApiReq(MUNICIPALITIES_API + "?region=" + region).GET().build();
        HttpResponse<String> resp = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() != 200) throw new IOException("Nova municipalities error: " + resp.statusCode());

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
        Session session = newSession();
        init(session);

        String region = encode((String) stateCtx.get("region"));
        String municipality = encode((String) munCtx.get("municipality"));
        
        Map<String, Map<String, Object>> allStreets = Collections.synchronizedMap(new LinkedHashMap<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Nova queries streets by their first letter. We fetch them concurrently.
        for (String letter : GREEK_ALPHABET) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String url = STREETS_API + encode(letter) + "?region=" + region + "&municipality=" + municipality;
                    HttpRequest req = buildApiReq(url).GET().build();
                    HttpResponse<String> resp = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() == 200) {
                        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
                        List<Map<String, Object>> results = extractResultsListOfMaps(data);
                        for (Map<String, Object> s : results) {
                            String streetName = (String) s.get("street");
                            Map<String, Object> ctx = new LinkedHashMap<>();
                            ctx.put("region", stateCtx.get("region"));
                            ctx.put("municipality", munCtx.get("municipality"));
                            ctx.put("city", s.get("city"));
                            ctx.put("street", streetName);
                            ctx.put("zipcode", s.get("zipcode"));
                            ctx.put("label", streetName);
                            allStreets.put(streetName, ctx);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("[Nova] Street fetch error for letter " + letter, e);
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return allStreets;
    }

    public List<Plan> checkAvailability(
            Map<String, Object> stateCtx,
            Map<String, Object> munCtx,
            Map<String, Object> streetCtx,
            String streetNumber) throws IOException, InterruptedException {
            
        Session session = newSession();
        init(session);

        Map<String, Object> payload = buildAvailabilityPayload(stateCtx, munCtx, streetCtx, streetNumber);
        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpRequest req = buildApiReq(AVAIL_API)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Nova availability check error: HTTP " + resp.statusCode());
        }

        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        return parseNovaPlans(data);
    }

    private Map<String, Object> buildAvailabilityPayload(
            Map<String, Object> stateCtx, Map<String, Object> munCtx, 
            Map<String, Object> streetCtx, String number) {

        Map<String, Object> payload = new LinkedHashMap<>();
        
        payload.put("packagePreselected", Map.of(
                "code", "2P_FIBER_100", 
                "title", "Fiber 100", 
                "price", "29.0"));
                
        payload.put("packageSelected", Map.of(
                "code", "", "title", "", "price", null, "packageGroupType", ""));
                
        payload.put("customerInfo", Map.of(
                "isNewCustomer", true, 
                "isExistingCustomerMoving", false, 
                "landlineNumber", ""));
                
        payload.put("address", Map.of(
                "region", stateCtx.get("region"),
                "municipality", munCtx.get("municipality"),
                "city", streetCtx.get("city"),
                "street", streetCtx.get("street"),
                "zipcode", streetCtx.get("zipcode"),
                "streetNumber", number));
                
        payload.put("userType", "Postpaid");
        payload.put("fixedPackagesType", "TwoP");
        payload.put("eligibleFixedPackagesType", null);

        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<Plan> parseNovaPlans(Map<String, Object> data) {
        List<Plan> plans = new ArrayList<>();
        Map<String, Object> result = (Map<String, Object>) data.getOrDefault("result", Map.of());
        List<Map<String, Object>> packages = (List<Map<String, Object>>) result.get("packages");

        if (packages != null) {
            Pattern speedPattern = Pattern.compile("(\\d+)");
            for (Map<String, Object> pkg : packages) {
                String title = (String) pkg.getOrDefault("title", "NOVA Unknown");
                Double maxDl = null;
                Double maxUl = null;
                
                Matcher m = speedPattern.matcher(title);
                if (m.find()) {
                    maxDl = Double.parseDouble(m.group(1));
                    maxUl = maxDl / 10.0;
                }
                
                plans.add(new Plan("NOVA", title, maxDl, maxUl));
            }
        }
        return plans;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractResultsList(Map<String, Object> data) {
        return (List<String>) data.getOrDefault("result", Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResultsListOfMaps(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.getOrDefault("result", Collections.emptyList());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}