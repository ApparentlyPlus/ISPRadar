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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VodafoneService {

    private static final Logger LOG = LoggerFactory.getLogger(VodafoneService.class);

    private static final String BASE = "https://www.vodafone.gr";
    private static final String HOME = BASE + "/statheri-internet-programmata";
    private static final String GEO_API = BASE + "/api/geographicAddress";
    private static final String AVAIL_API = BASE + "/api/eligibilityTool/queryServiceQualification";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    private static final String PARAM_STATE = "externalIdentifier[?(externalIdentifierType==\"stateOrProvince\")].id";
    private static final String PARAM_CITY = "externalIdentifier[?(externalIdentifierType==\"city\")].id";
    private static final String FIELDS_STATE = "stateOrProvince," + PARAM_STATE;
    private static final String FIELDS_CITY = "city," + PARAM_CITY;
    private static final String FIELDS_POSTAL = "postcode";
    private static final String FIELDS_STREET = "streetName";
    private static final String FIELDS_NUMBER = "streetNr,streetNrSuffix";

    private final ObjectMapper objectMapper;

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

    public VodafoneService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        LOG.info("[Vodafone] Initialising session...");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(HOME))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("sec-fetch-mode", "navigate")
                .header("sec-fetch-site", "none")
                .GET()
                .build();
        HttpResponse<String> r = session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) {
            throw new IOException("Vodafone session init failed: HTTP " + r.statusCode());
        }
        session.initialized = true;
    }

    private URI buildGeoUri(Map<String, String> params) {
        StringBuilder sb = new StringBuilder(GEO_API).append("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append("&");
            first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(sb.toString());
    }

    private HttpRequest buildApiGet(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .GET()
                .build();
    }

    private Map<String, Map<String, Object>> fetchOptions(Session session, Map<String, String> params)
            throws IOException, InterruptedException {
        init(session);
        URI uri = buildGeoUri(params);
        HttpResponse<String> resp = session.httpClient.send(buildApiGet(uri), HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 404) return Collections.emptyMap();
        if (resp.statusCode() == 403 || resp.statusCode() == 302) {
            session.cookieManager.getCookieStore().removeAll();
            session.initialized = false;
            init(session);
            resp = session.httpClient.send(buildApiGet(uri), HttpResponse.BodyHandlers.ofString());
        }
        if (resp.statusCode() != 200) {
            throw new IOException("Vodafone geo API error: HTTP " + resp.statusCode());
        }

        List<Map<String, Object>> items = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String label = ((String) item.getOrDefault("label", "")).strip();
            if (!label.isEmpty()) result.put(label, item);
        }
        return result;
    }

    public Map<String, Map<String, Object>> fetchStates() throws IOException, InterruptedException {
        Session session = newSession();
        return fetchOptions(session, Map.of("fields", FIELDS_STATE));
    }

    public Map<String, Map<String, Object>> fetchCities(Map<String, Object> stateCtx)
            throws IOException, InterruptedException {
        Session session = newSession();
        return fetchOptions(session, Map.of(
                "fields", FIELDS_CITY,
                PARAM_STATE, (String) stateCtx.get("value")));
    }

    public Map<String, Map<String, Object>> fetchPostalCodes(
            Map<String, Object> stateCtx,
            Map<String, Object> cityCtx) throws IOException, InterruptedException {
        Session session = newSession();
        return fetchOptions(session, Map.of(
                "fields", FIELDS_POSTAL,
                PARAM_STATE, (String) stateCtx.get("value"),
                PARAM_CITY, (String) cityCtx.get("value")));
    }

    public Map<String, Map<String, Object>> fetchStreets(
            Map<String, Object> stateCtx,
            Map<String, Object> cityCtx,
            Map<String, Object> postalCtx) throws IOException, InterruptedException {
        Session session = newSession();
        return fetchOptions(session, Map.of(
                "fields", FIELDS_STREET,
                PARAM_STATE, (String) stateCtx.get("value"),
                PARAM_CITY, (String) cityCtx.get("value"),
                "postcode", (String) postalCtx.get("value")));
    }

    public Map<String, Map<String, Object>> fetchNumbers(
            Map<String, Object> stateCtx,
            Map<String, Object> cityCtx,
            Map<String, Object> postalCtx,
            Map<String, Object> streetCtx) throws IOException, InterruptedException {
        Session session = newSession();
        return fetchOptions(session, Map.of(
                "fields", FIELDS_NUMBER,
                PARAM_STATE, (String) stateCtx.get("value"),
                PARAM_CITY, (String) cityCtx.get("value"),
                "postcode", (String) postalCtx.get("value"),
                "streetName", (String) streetCtx.get("value")));
    }

    @SuppressWarnings("unchecked")
    public List<Plan> checkAvailability(
            Map<String, Object> stateCtx,
            Map<String, Object> cityCtx,
            Map<String, Object> postalCtx,
            Map<String, Object> streetCtx,
            Map<String, Object> numberCtx) throws IOException, InterruptedException {

        Session session = newSession();
        init(session);

        String json = buildCheckPayload(stateCtx, cityCtx, postalCtx, streetCtx, numberCtx, null);
        HttpResponse<String> resp = sendCheckRequest(session, json);

        if (resp.statusCode() == 403 || resp.statusCode() == 302) {
            session.cookieManager.getCookieStore().removeAll();
            session.initialized = false;
            init(session);
            resp = sendCheckRequest(session, json);
        }

        Map<String, Object> data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        if ("showFloorDropdown".equals(data.get("renderScenario"))) {
            LOG.info("[Vodafone] Floor dropdown required");
            Map<String, Object> floor = Map.of("label", "Ισόγειο", "value", "O00");
            String retryJson = buildCheckPayload(stateCtx, cityCtx, postalCtx, streetCtx, numberCtx, floor);
            resp = sendCheckRequest(session, retryJson);
            data = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        }

        return parseVodafonePlans(data);
    }

    private HttpResponse<String> sendCheckRequest(Session session, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AVAIL_API))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, */*")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return session.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String buildCheckPayload(
            Map<String, Object> stateCtx,
            Map<String, Object> cityCtx,
            Map<String, Object> postalCtx,
            Map<String, Object> streetCtx,
            Map<String, Object> numberCtx,
            Map<String, Object> floor) throws IOException {

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("stateOrProvince", stateCtx);
        address.put("city", cityCtx);
        address.put("postcode", postalCtx);
        address.put("streetName", streetCtx);
        address.put("streetNrSuffix", numberCtx);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("address", address);
        if (floor != null) requestBody.put("buildingDetails", Map.of("floor", floor));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerType", "Retail");
        payload.put("requestBody", requestBody);
        payload.put("searchCriteria", "byAddress");
        payload.put("providerData", Map.of(
                "providers", Map.of("COSMOTE", "OTE", "VODAFONE", "VODAFONE"),
                "multipleBuildingsErrorCodes", List.of("FTTH_ELIGIBILITY_ERR_005", "FTTH_ELIGIBILITY_ERR_006"),
                "allHiddenPrograms", List.of("VDSL_30"),
                "businessHidenPrograms", List.of("ADSL")));
        payload.put("isFirstCheck", floor == null);

        return objectMapper.writeValueAsString(payload);
    }

    @SuppressWarnings("unchecked")
    private List<Plan> parseVodafonePlans(Map<String, Object> data) {
        List<Plan> plans = new ArrayList<>();
        List<Map<String, Object>> speeds =
                (List<Map<String, Object>>) data.getOrDefault("availableSpeeds", List.of());
        for (Map<String, Object> plan : speeds) {
            String name = (String) plan.getOrDefault("name", "Unknown Package");
            Map<String, Object> s = (Map<String, Object>) plan.getOrDefault("speeds", Map.of());
            plans.add(new Plan("VODAFONE", name, toDouble(s.get("maxPromisedSpeedDownload")),
                    toDouble(s.get("maxPromisedSpeedUpload"))));
        }
        return plans;
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}