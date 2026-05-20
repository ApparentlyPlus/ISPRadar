package com.ispradar.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispradar.backend.config.CosmoteConfig;
import com.ispradar.backend.dto.Plan;
import com.ispradar.backend.service.PlanCatalog.PlanMetadata;
import com.ispradar.backend.util.http.BaseIspHttpClient;
import com.ispradar.backend.util.http.HttpStatusCode;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CosmoteService extends BaseIspHttpClient {

    private static final Map<String, String> STATES;
    static {
        STATES = new LinkedHashMap<>();
        STATES.put("ΑΓΙΟ ΟΡΟΣ", "39");
        STATES.put("ΑΙΤΩΛΟΑΚΑΡΝΑΝΙΑΣ", "1");
        STATES.put("ΑΡΓΟΛΙΔΑΣ", "7");
        STATES.put("ΑΡΚΑΔΙΑΣ", "8");
        STATES.put("ΑΡΤΑΣ", "18");
        STATES.put("ΑΤΤΙΚΗΣ", "52");
        STATES.put("ΑΧΑΪΑΣ", "9");
        STATES.put("ΒΟΙΩΤΙΑΣ", "2");
        STATES.put("ΓΡΕΒΕΝΩΝ", "26");
        STATES.put("ΔΡΑΜΑΣ", "27");
        STATES.put("ΔΩΔΕΚΑΝΗΣΟΥ", "43");
        STATES.put("ΕΒΡΟΥ", "40");
        STATES.put("ΕΥΒΟΙΑΣ", "3");
        STATES.put("ΕΥΡΥΤΑΝΙΑΣ", "4");
        STATES.put("ΖΑΚΥΝΘΟΥ", "14");
        STATES.put("ΗΛΕΙΑΣ", "10");
        STATES.put("ΗΜΑΘΙΑΣ", "28");
        STATES.put("ΗΡΑΚΛΕΙΟΥ", "48");
        STATES.put("ΘΕΣΠΡΩΤΙΑΣ", "19");
        STATES.put("ΘΕΣΣΑΛΟΝΙΚΗΣ", "29");
        STATES.put("ΙΩΑΝΝΙΝΩΝ", "20");
        STATES.put("ΚΑΒΑΛΑΣ", "30");
        STATES.put("ΚΑΡΔΙΤΣΑΣ", "22");
        STATES.put("ΚΑΣΤΟΡΙΑΣ", "31");
        STATES.put("ΚΕΡΚΥΡΑΣ", "15");
        STATES.put("ΚΕΦΑΛΛΟΝΙΑΣ", "16");
        STATES.put("ΚΙΛΚΙΣ", "32");
        STATES.put("ΚΟΖΑΝΗΣ", "33");
        STATES.put("ΚΟΡΙΝΘΙΑΣ", "11");
        STATES.put("ΚΥΚΛΑΔΩΝ", "44");
        STATES.put("ΛΑΚΩΝΙΑΣ", "12");
        STATES.put("ΛΑΡΙΣΑΣ", "23");
        STATES.put("ΛΑΣΙΘΙΟΥ", "49");
        STATES.put("ΛΕΣΒΟΥ", "45");
        STATES.put("ΛΕΥΚΑΔΑΣ", "17");
        STATES.put("ΜΑΓΝΗΣΙΑΣ", "24");
        STATES.put("ΜΕΣΣΗΝΙΑΣ", "13");
        STATES.put("ΞΑΝΘΗΣ", "41");
        STATES.put("ΠΕΛΛΑΣ", "34");
        STATES.put("ΠΙΕΡΙΑΣ", "35");
        STATES.put("ΠΡΕΒΕΖΑΣ", "21");
        STATES.put("ΡΕΘΥΜΝΟΥ", "50");
        STATES.put("ΡΟΔΟΠΗΣ", "42");
        STATES.put("ΣΑΜΟΥ", "46");
        STATES.put("ΣΕΡΡΩΝ", "36");
        STATES.put("ΤΡΙΚΑΛΩΝ", "25");
        STATES.put("ΦΘΙΩΤΙΔΑΣ", "5");
        STATES.put("ΦΛΩΡΙΝΑΣ", "37");
        STATES.put("ΦΩΚΙΔΑΣ", "6");
        STATES.put("ΧΑΛΚΙΔΙΚΗΣ", "38");
        STATES.put("ΧΑΝΙΩΝ", "51");
        STATES.put("ΧΙΟΥ", "47");
    }

    public CosmoteService(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected String getProviderName() { return "Cosmote"; }

    @Override
    protected void initializeSession() throws IOException, InterruptedException {
        var request = buildInit();
        HttpResponse<String> r = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (HttpStatusCode.isError(r.statusCode())) 
            throw new IOException("Session init failed: HTTP " + r.statusCode());
    }

    @Override
    protected HttpRequest buildInit() {
        return HttpRequest.newBuilder()
                .uri(URI.create(CosmoteConfig.START_URL))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", CosmoteConfig.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .header("Accept-Language", "en-US,en;q=0.9,el;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();
    }

    @Override
    protected HttpRequest buildGet(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", CosmoteConfig.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .header("Accept-Language", "en-US,en;q=0.9,el;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();
    }

    @Override
    protected HttpRequest buildPost(String url, String jsonPayload){
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", CosmoteConfig.USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9,el;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
    }

    private String buildDropUrl(Map<String, String> params) {
        StringBuilder sb = new StringBuilder(CosmoteConfig.DROP_API).append("?");
        params.forEach((k, v) -> {
            String enc = URLEncoder.encode(v, StandardCharsets.UTF_8)
                    .replace("%28", "(")
                    .replace("%29", ")");
            sb.append(k).append("=").append(enc).append("&");
        });
        sb.append("_=").append(System.currentTimeMillis());
        return sb.toString();
    }

    private Map<String, String> fetchOptions(Map<String, String> params)
            throws IOException, InterruptedException {
        String url = buildDropUrl(params);
        HttpResponse<String> resp = executeWithRetry(buildGet(url));
        return parseHtmlOptions(resp.body());
    }

    private Map<String, String> parseHtmlOptions(String html) {
        Document doc = Jsoup.parse(html);
        Map<String, String> opts = new LinkedHashMap<>();
        for (Element el : doc.select("option, a")) {
            String val = el.hasAttr("value") ? el.attr("value") : el.id();
            String text = el.text().strip();
            val = val.strip();
            if (!val.isEmpty() && !val.equals("-1") && !val.equals("0")
                    && !text.isEmpty() && !text.contains("Επιλέξ")) {
                opts.put(text, val);
            }
        }
        return opts;
    }

    public Map<String, Map<String, Object>> fetchStates() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        STATES.forEach((label, id) -> result.put(label, Map.of("stateId", id, "label", label)));
        return result;
    }

    public Map<String, Map<String, Object>> fetchMunicipalities(Map<String, Object> stateCtx)
            throws IOException, InterruptedException {
        String stateId = (String) stateCtx.get("stateId");
        Map<String, String> raw = fetchOptions(Map.of("stateId", stateId, "removePrefix", "true"));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, munId) -> result.put(label, Map.of(
                "stateId", stateId,
                "stateLabel", stateCtx.get("label"),
                "municipalityId", munId,
                "label", label)));
        return result;
    }

    public Map<String, Map<String, Object>> fetchStreets(Map<String, Object> munCtx)
            throws IOException, InterruptedException {
        String stateId = (String) munCtx.get("stateId");
        String munId = (String) munCtx.get("municipalityId");
        Map<String, String> raw = fetchOptions(Map.of("stateId", stateId, "municipalityId", munId));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, ignored) -> result.put(label, Map.of(
                "stateId", stateId,
                "stateLabel", munCtx.get("stateLabel"),
                "municipalityId", munId,
                "municipalityLabel", munCtx.get("label"),
                "streetName", label,
                "label", label)));
        return result;
    }

    public Map<String, Map<String, Object>> fetchAreas(Map<String, Object> streetCtx)
            throws IOException, InterruptedException {
    Map<String, String> raw = fetchOptions(Map.of(
                "streetName", ((String) streetCtx.get("streetName")).toUpperCase(),
                "stateId", (String) streetCtx.get("stateId"),
                "municipalityId", (String) streetCtx.get("municipalityId")));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, ignored) -> result.put(label, Map.of(
                "stateLabel", streetCtx.get("stateLabel"),
                "municipalityLabel", streetCtx.get("municipalityLabel"),
                "streetName", streetCtx.get("streetName"),
                "areaName", label,
                "label", label)));
        return result;
    }

    public List<Plan> checkAvailability(
            Map<String, Object> stateCtx,
            Map<String, Object> munCtx,
            Map<String, Object> areaCtx,
            Map<String, Object> streetCtx,
            String number) throws IOException, InterruptedException {
        ensureInitialized();

        Map<String, String> form = new LinkedHashMap<>();
        form.put("mTelno", "");
        form.put("mState", "Ν. " + stateCtx.get("label"));
        form.put("mPrefecture", "Δ. " + munCtx.get("label"));
        form.put("mArea", (String) areaCtx.get("areaName"));
        form.put("mAddress", ((String) streetCtx.get("streetName")).toUpperCase());
        form.put("mNumber", number);
        form.put("searchcriteria", "address");
        form.put("ct", "res");

        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = buildPost(CosmoteConfig.AVAIL_API, body);
        HttpResponse<String> resp = executeWithRetry(req);
        return parseCosmotePlans(resp.body());
    }

    private List<Plan> parseCosmotePlans(String html) {
        Document doc = Jsoup.parse(html);
        List<Plan> out = new ArrayList<>();
        for (Element c : doc.select("div[class~=available-programm-container]")) {
            if (c.attr("style").replace(" ", "").contains("display:none")) continue;
            var greens = c.select("div.light-green");
            if (greens.isEmpty()) continue;
            String rawName = greens.get(0).wholeText().replaceAll("\\s+", " ").strip();
            Double maxMbps = extractSpeedMbps(rawName);
            if (maxMbps == null) continue;
            String type = inferType(rawName, maxMbps);
            Double dl = computeDownload(maxMbps);
            Double ul = computeUpload(dl);
            PlanMetadata meta = PlanCatalog.lookupCosmoteByKey(type);
            String name = meta != null ? meta.name() : rawName;
            Double price = meta != null ? meta.price() : null;
            List<String> description = meta != null ? meta.description() : List.of();
            out.add(new Plan("COSMOTE", name, round2(dl), round2(ul), price, description));
        }
        return out;
    }

    private static final Pattern SPEED_GBPS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*GBPS", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEED_MBPS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*MBPS", Pattern.CASE_INSENSITIVE);

    private Double extractSpeedMbps(String name) {
        Matcher gb = SPEED_GBPS.matcher(name);
        if (gb.find()) {
            return Double.parseDouble(gb.group(1)) * 1000.0;
        }
        Matcher mb = SPEED_MBPS.matcher(name);
        if (mb.find()) {
            return Double.parseDouble(mb.group(1));
        }
        return null;
    }

    private String inferType(String name, double maxMbps) {
        String upper = name.toUpperCase();
        if (upper.contains("ADSL")) {
            return "ADSL_" + Math.round(maxMbps);
        }
        if (upper.contains("VDSL")) {
            return "VDSL_" + Math.round(maxMbps);
        }
        if (upper.contains("FIBER") || maxMbps >= 100) {
            return "FIBER_" + Math.round(maxMbps);
        }
        return "PLAN_" + Math.round(maxMbps);
    }

    private Double computeDownload(double maxMbps) {
        return maxMbps;
    }

    private Double computeUpload(double downloadMbps) {
        return downloadMbps / 2.0;
    }

    private Double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}