package com.ispradar.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import com.ispradar.backend.dto.Plan;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CosmoteService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String BASE       = "https://www.cosmote.gr";
    private static final String START_URL  = BASE + "/eshop/jsp/diathesimotita-adsl-vdsl-cosmotetv.jsp?ct=res";
    private static final String DROP_API   = BASE + "/eshop/global/gadgets/populateAddressDetailsV3.jsp";
    private static final String AVAIL_API  = BASE + "/eshop/jsp/ajax/avdslavailabilityAjaxV2.jsp";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Hardcoded Cosmote prefecture map: display name → stateId */
    private static final Map<String, String> STATES;
    static {
        STATES = new LinkedHashMap<>();
        STATES.put("ΑΓΙΟ ΟΡΟΣ",      "39"); STATES.put("ΑΙΤΩΛΟΑΚΑΡΝΑΝΙΑΣ", "1");
        STATES.put("ΑΡΓΟΛΙΔΑΣ",      "7");  STATES.put("ΑΡΚΑΔΙΑΣ",          "8");
        STATES.put("ΑΡΤΑΣ",          "18"); STATES.put("ΑΤΤΙΚΗΣ",            "52");
        STATES.put("ΑΧΑΪΑΣ",         "9");  STATES.put("ΒΟΙΩΤΙΑΣ",           "2");
        STATES.put("ΓΡΕΒΕΝΩΝ",       "26"); STATES.put("ΔΡΑΜΑΣ",             "27");
        STATES.put("ΔΩΔΕΚΑΝΗΣΟΥ",    "43"); STATES.put("ΕΒΡΟΥ",              "40");
        STATES.put("ΕΥΒΟΙΑΣ",        "3");  STATES.put("ΕΥΡΥΤΑΝΙΑΣ",         "4");
        STATES.put("ΖΑΚΥΝΘΟΥ",       "14"); STATES.put("ΗΛΕΙΑΣ",             "10");
        STATES.put("ΗΜΑΘΙΑΣ",        "28"); STATES.put("ΗΡΑΚΛΕΙΟΥ",          "48");
        STATES.put("ΘΕΣΠΡΩΤΙΑΣ",     "19"); STATES.put("ΘΕΣΣΑΛΟΝΙΚΗΣ",       "29");
        STATES.put("ΙΩΑΝΝΙΝΩΝ",      "20"); STATES.put("ΚΑΒΑΛΑΣ",            "30");
        STATES.put("ΚΑΡΔΙΤΣΑΣ",      "22"); STATES.put("ΚΑΣΤΟΡΙΑΣ",          "31");
        STATES.put("ΚΕΡΚΥΡΑΣ",       "15"); STATES.put("ΚΕΦΑΛΛΟΝΙΑΣ",        "16");
        STATES.put("ΚΙΛΚΙΣ",         "32"); STATES.put("ΚΟΖΑΝΗΣ",            "33");
        STATES.put("ΚΟΡΙΝΘΙΑΣ",      "11"); STATES.put("ΚΥΚΛΑΔΩΝ",           "44");
        STATES.put("ΛΑΚΩΝΙΑΣ",       "12"); STATES.put("ΛΑΡΙΣΑΣ",            "23");
        STATES.put("ΛΑΣΙΘΙΟΥ",       "49"); STATES.put("ΛΕΣΒΟΥ",             "45");
        STATES.put("ΛΕΥΚΑΔΑΣ",       "17"); STATES.put("ΜΑΓΝΗΣΙΑΣ",          "24");
        STATES.put("ΜΕΣΣΗΝΙΑΣ",      "13"); STATES.put("ΞΑΝΘΗΣ",             "41");
        STATES.put("ΠΕΛΛΑΣ",         "34"); STATES.put("ΠΙΕΡΙΑΣ",            "35");
        STATES.put("ΠΡΕΒΕΖΑΣ",       "21"); STATES.put("ΡΕΘΥΜΝΟΥ",           "50");
        STATES.put("ΡΟΔΟΠΗΣ",        "42"); STATES.put("ΣΑΜΟΥ",              "46");
        STATES.put("ΣΕΡΡΩΝ",         "36"); STATES.put("ΤΡΙΚΑΛΩΝ",           "25");
        STATES.put("ΦΘΙΩΤΙΔΑΣ",      "5");  STATES.put("ΦΛΩΡΙΝΑΣ",           "37");
        STATES.put("ΦΩΚΙΔΑΣ",        "6");  STATES.put("ΧΑΛΚΙΔΙΚΗΣ",         "38");
        STATES.put("ΧΑΝΙΩΝ",         "51"); STATES.put("ΧΙΟΥ",               "47");
    }

    // ── HTTP session ──────────────────────────────────────────────────────────

    private final CookieManager cookieManager;
    private final HttpClient     httpClient;
    private final AtomicBoolean  sessionReady = new AtomicBoolean(false);
    private final ReentrantLock  sessionLock  = new ReentrantLock();

    public CosmoteService() {
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient    = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private void doInit() throws IOException, InterruptedException {
        log.info("[Cosmote] Initialising session...");
        HttpResponse<String> r = httpClient.send(
                buildGet(START_URL, "text/html,application/xhtml+xml,*/*"),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200)
            throw new IOException("Cosmote session init failed: HTTP " + r.statusCode());
        log.info("[Cosmote] Session ready");
    }

    private void ensureSession() {
        if (sessionReady.get()) return;
        sessionLock.lock();
        try {
            if (!sessionReady.get()) { doInit(); sessionReady.set(true); }
        } catch (Exception e) {
            throw new RuntimeException("Cosmote session init failed", e);
        } finally {
            sessionLock.unlock();
        }
    }

    private void resetSession() {
        sessionLock.lock();
        try {
            log.warn("[Cosmote] Resetting session...");
            sessionReady.set(false);
            cookieManager.getCookieStore().removeAll();
            doInit();
            sessionReady.set(true);
        } catch (Exception e) {
            throw new RuntimeException("Cosmote session reset failed", e);
        } finally {
            sessionLock.unlock();
        }
    }

    // ── Request builders ──────────────────────────────────────────────────────

    private HttpRequest buildGet(String url, String accept) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent",        USER_AGENT)
                .header("Accept",            accept)
                .header("Accept-Language",   "en-US,en;q=0.9,el;q=0.8")
                .header("X-Requested-With",  "XMLHttpRequest")
                .GET().build();
    }

    /**
     * Mirrors Python: urllib.parse.quote(v, safe='()')
     * — keys are NOT encoded, values are percent-encoded with ( ) left as-is.
     */
    private String buildDropUrl(Map<String, String> params) {
        StringBuilder sb = new StringBuilder(DROP_API).append("?");
        params.forEach((k, v) -> {
            String enc = URLEncoder.encode(v, StandardCharsets.UTF_8)
                    .replace("%28", "(").replace("%29", ")");
            sb.append(k).append("=").append(enc).append("&");
        });
        sb.append("_=").append(System.currentTimeMillis());
        return sb.toString();
    }

    // ── Internal fetch helper ─────────────────────────────────────────────────

    /**
     * Fetches a dropdown URL, returns {displayText → optionValue} map.
     * Mirrors CosmoteScraper._fetch_options().
     */
    private Map<String, String> fetchOptions(Map<String, String> params)
            throws IOException, InterruptedException {
        ensureSession();
        String url = buildDropUrl(params);
        HttpResponse<String> resp = httpClient.send(
                buildGet(url, "text/html,application/xhtml+xml,*/*"),
                HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 403 || resp.statusCode() == 302) {
            resetSession();
            resp = httpClient.send(
                    buildGet(buildDropUrl(params), "text/html,application/xhtml+xml,*/*"),
                    HttpResponse.BodyHandlers.ofString());
        }
        return parseHtmlOptions(resp.body());
    }

    /** Parses <option> and <a> tags the same way BeautifulSoup does in the Python script. */
    private Map<String, String> parseHtmlOptions(String html) {
        Document doc = Jsoup.parse(html);
        Map<String, String> opts = new LinkedHashMap<>();
        for (Element el : doc.select("option, a")) {
            String val  = el.hasAttr("value") ? el.attr("value") : el.id();
            String text = el.text().strip();
            val = val.strip();
            if (!val.isEmpty() && !val.equals("-1") && !val.equals("0")
                    && !text.isEmpty() && !text.contains("Επιλέξ")) {
                opts.put(text, val);
            }
        }
        return opts;
    }

    // ── Public API (called by orchestrator) ───────────────────────────────────

    /** Step 1 — no HTTP call needed, hardcoded map. */
    public Map<String, Map<String, Object>> fetchStates() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        STATES.forEach((label, id) ->
                result.put(label, Map.of("stateId", id, "label", label)));
        return result;
    }

    /** Step 2 — municipalities for a given state. */
    public Map<String, Map<String, Object>> fetchMunicipalities(Map<String, Object> stateCtx)
            throws IOException, InterruptedException {
        String stateId = (String) stateCtx.get("stateId");
        Map<String, String> raw = fetchOptions(Map.of("stateId", stateId, "removePrefix", "true"));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, munId) ->
                result.put(label, Map.of(
                        "stateId",        stateId,
                        "stateLabel",     stateCtx.get("label"),
                        "municipalityId", munId,
                        "label",          label)));
        return result;
    }

    /** Step 3 (Cosmote) — streets for a given municipality. */
    public Map<String, Map<String, Object>> fetchStreets(Map<String, Object> munCtx)
            throws IOException, InterruptedException {
        String stateId = (String) munCtx.get("stateId");
        String munId   = (String) munCtx.get("municipalityId");
        Map<String, String> raw = fetchOptions(Map.of("stateId", stateId, "municipalityId", munId));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, ignored) ->
                result.put(label, Map.of(
                        "stateId",         stateId,
                        "stateLabel",      munCtx.get("stateLabel"),
                        "municipalityId",  munId,
                        "municipalityLabel", munCtx.get("label"),
                        "streetName",      label,   // Cosmote uses text, not option value
                        "label",           label)));
        return result;
    }

    /** Step 4 (Cosmote) — areas for a given street. */
    public Map<String, Map<String, Object>> fetchAreas(Map<String, Object> streetCtx)
            throws IOException, InterruptedException {
        Map<String, String> raw = fetchOptions(Map.of(
                "streetName",    ((String) streetCtx.get("streetName")).toUpperCase(),
                "stateId",       (String) streetCtx.get("stateId"),
                "municipalityId",(String) streetCtx.get("municipalityId")));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((label, ignored) ->
                result.put(label, Map.of(
                        "stateLabel",        streetCtx.get("stateLabel"),
                        "municipalityLabel", streetCtx.get("municipalityLabel"),
                        "streetName",        streetCtx.get("streetName"),
                        "areaName",          label,
                        "label",             label)));
        return result;
    }

    /** Step 5 — final availability POST. */
    public List<Plan> checkAvailability(
            Map<String, Object> stateCtx,
            Map<String, Object> munCtx,
            Map<String, Object> areaCtx,
            Map<String, Object> streetCtx,
            String number) throws IOException, InterruptedException {

        ensureSession();

        // Mirrors Python payload exactly
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mTelno",        "");
        form.put("mState",        "Ν. " + stateCtx.get("label"));
        form.put("mPrefecture",   "Δ. " + munCtx.get("label"));
        form.put("mArea",         (String) areaCtx.get("areaName"));
        form.put("mAddress",      ((String) streetCtx.get("streetName")).toUpperCase());
        form.put("mNumber",       number);
        form.put("searchcriteria","address");
        form.put("ct",            "res");

        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                          + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AVAIL_API))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent",    USER_AGENT)
                .header("Accept-Language","en-US,en;q=0.9,el;q=0.8")
                .header("X-Requested-With","XMLHttpRequest")
                .header("Content-Type",  "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 403) { resetSession(); resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); }

        return parseCosmotePlans(resp.body());
    }

    private List<Plan> parseCosmotePlans(String html) {
        Document doc   = Jsoup.parse(html);
        List<Plan> out = new ArrayList<>();
        for (Element c : doc.select("div[class~=available-programm-container]")) {
            if (c.attr("style").replace(" ", "").contains("display:none")) continue;
            var greens = c.select("div.light-green");
            if (greens.isEmpty()) continue;
            String name   = greens.get(0).wholeText().replaceAll("\\s+", " ").strip();
            String status = greens.size() > 1 ? " | " + greens.get(1).text().strip() : "";
            out.add(new Plan("COSMOTE", name + status, null, null));
        }
        return out;
    }
}