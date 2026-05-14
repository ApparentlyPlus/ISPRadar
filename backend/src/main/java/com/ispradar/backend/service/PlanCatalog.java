package com.ispradar.backend.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlanCatalog {

    public record PlanMetadata(String name, Double price, List<String> description) {}

    private static final List<String> COSMOTE_BASE_DESC = List.of(
            "Απεριόριστα λεπτά προς σταθερά",
            "Απεριόριστα λεπτά προς κινητά",
            "Δωρεάν ασύρματο router τελευταίας τεχνολογίας"
    );

    private static final List<String> COSMOTE_FIBER_DESC = List.of(
            "Απεριόριστα λεπτά προς σταθερά",
            "Απεριόριστα λεπτά προς κινητά",
            "100% οπτική ίνα μέχρι την πρίζα του σπιτιού σου",
            "ΕΓΓΥΗΣΗ ΚΑΛΗΣ ΕΓΚΑΤΑΣΤΑΣΗΣ",
            "ΔΩΡΟ πακέτο COSMOTE TV Start",
            "Δωρεάν ασύρματο router τελευταίας τεχνολογίας"
    );

    private static final List<String> NOVA_ROUTER_100_DESC = List.of(
            "Δωρεάν παροχή χρήσης Wi-Fi VoIP Router",
            "Απεριόριστες εθνικές κλήσεις προς σταθερά και κινητά"
    );

    private static final List<String> NOVA_ROUTER_WIFI6_DESC = List.of(
            "Δωρεάν χρήση Wi-Fi 6 router για ασυναγώνιστη εμπειρία Internet",
            "Απεριόριστες εθνικές κλήσεις προς σταθερά και κινητά"
    );

    private static final List<String> NOVA_EON_PLUS_DESC = List.of(
            "EON TV, 113/88 HD κανάλια",
            "14 Novasports, 11 Cosmote Sport εκ των οποίων 1 σε 4Κ ανάλυση, 4 Novacinema, 8 παιδικά κανάλια και πολλά άλλα!",
            "6.000+ Ταινίες & Επεισόδια Σειρών On Demand"
    );

    private static final List<String> NOVA_EON_DESC = List.of(
            "EON TV, 89/65 HD κανάλια",
            "4 Novacinema, 2 Novasports, 8 παιδικά κανάλια και πολλά άλλα!",
            "6.000+ Ταινίες & Επεισόδια Σειρών On Demand"
    );

    private static final List<String> VODAFONE_ADSL_DESC = List.of(
            "Απεριόριστα λεπτά προς σταθερά",
            "300' προς κινητά Ελλάδος"
    );

    private static final List<String> VODAFONE_VDSL_DESC = List.of(
            "Απεριόριστα λεπτά προς σταθερά",
            "360' προς κινητά Ελλάδος & διεθνή σταθερά 45 χωρών"
    );

    private static final List<String> VODAFONE_FIBER_PLUS_DESC = List.of(
            "Απεριόριστα λεπτά προς σταθερά Ελλάδος",
            "Απεριόριστα λεπτά προς κινητά Ελλάδος"
    );

    private static final Map<String, PlanMetadata> COSMOTE_BY_KEY = Map.ofEntries(
            Map.entry("ADSL_24", new PlanMetadata("Double Play Unlimited", 19.90, COSMOTE_BASE_DESC)),
            Map.entry("VDSL_50", new PlanMetadata("Double Play Advanced Unlimited", 22.90, COSMOTE_BASE_DESC)),
            Map.entry("FIBER_50", new PlanMetadata("Double Play Advanced Unlimited", 22.90, COSMOTE_BASE_DESC)),
            Map.entry("FIBER_100", new PlanMetadata("FIBER 100 Unlimited", 24.90, COSMOTE_FIBER_DESC)),
            Map.entry("FIBER_200", new PlanMetadata("Fiber 200 Unlimited", 52.15, COSMOTE_FIBER_DESC)),
            Map.entry("FIBER_300", new PlanMetadata("FIBER 300 Unlimited", 27.90, COSMOTE_FIBER_DESC)),
            Map.entry("FIBER_500", new PlanMetadata("FIBER 500 Unlimited", 31.90, COSMOTE_FIBER_DESC)),
            Map.entry("FIBER_1000", new PlanMetadata("FIBER 1Gbps Unlimited", 35.90, COSMOTE_FIBER_DESC)),
            Map.entry("FIBER_3000", new PlanMetadata("FIBER 3Gbps Unlimited", 70.39, COSMOTE_FIBER_DESC))
    );

    private static final Map<String, PlanMetadata> NOVA_BY_NAME;
    private static final Map<String, PlanMetadata> VODAFONE_BY_NAME;

    static {
        Map<String, PlanMetadata> nova = new LinkedHashMap<>();
        nova.put(normalizeKey("Fiber 100"), new PlanMetadata("Fiber 100", 29.0, NOVA_ROUTER_100_DESC));
        nova.put(normalizeKey("Fiber 300"), new PlanMetadata("Fiber 300", 26.0, NOVA_ROUTER_WIFI6_DESC));
        nova.put(normalizeKey("Fiber 500"), new PlanMetadata("Fiber 500", 29.0, NOVA_ROUTER_WIFI6_DESC));
        nova.put(normalizeKey("Fiber 1Giga"), new PlanMetadata("Fiber 1Giga", 35.0, NOVA_ROUTER_WIFI6_DESC));
        nova.put(normalizeKey("Fiber 3Giga"), new PlanMetadata("Fiber 3Giga", 54.0, NOVA_ROUTER_WIFI6_DESC));
        nova.put(normalizeKey("Fiber 100 EON+"), new PlanMetadata(
                "Fiber 100 EON+",
                48.0,
                concat(NOVA_ROUTER_100_DESC, NOVA_EON_PLUS_DESC)));
        nova.put(normalizeKey("Fiber 100 EON"), new PlanMetadata(
                "Fiber 100 EON",
                35.0,
                concat(NOVA_ROUTER_100_DESC, NOVA_EON_DESC)));
        nova.put(normalizeKey("Fiber 300 EON+"), new PlanMetadata(
                "Fiber 300 EON+",
                45.0,
                concat(NOVA_ROUTER_WIFI6_DESC, NOVA_EON_PLUS_DESC)));
        nova.put(normalizeKey("Fiber 300 EON"), new PlanMetadata(
                "Fiber 300 EON",
                30.0,
                concat(NOVA_ROUTER_WIFI6_DESC, NOVA_EON_DESC)));
        nova.put(normalizeKey("Fiber 500 EON+"), new PlanMetadata(
                "Fiber 500 EON+",
                48.0,
                concat(NOVA_ROUTER_WIFI6_DESC, NOVA_EON_PLUS_DESC)));
        nova.put(normalizeKey("Fiber 500 EON"), new PlanMetadata(
                "Fiber 500 EON",
                35.0,
                concat(NOVA_ROUTER_WIFI6_DESC, NOVA_EON_DESC)));
        NOVA_BY_NAME = Map.copyOf(nova);

        Map<String, PlanMetadata> vodafone = new LinkedHashMap<>();
        vodafone.put(normalizeKey("Vodafone ADSL 24"), new PlanMetadata("Vodafone ADSL 24", 21.0, VODAFONE_ADSL_DESC));
        vodafone.put(normalizeKey("Vodafone VDSL 50"), new PlanMetadata("Vodafone VDSL 50", 26.0, VODAFONE_VDSL_DESC));
        vodafone.put(normalizeKey("Vodafone Fiber 100"), new PlanMetadata("Vodafone Fiber 100", 26.0, VODAFONE_VDSL_DESC));
        vodafone.put(normalizeKey("Vodafone Full Fiber 200 Plus"), new PlanMetadata("Vodafone Full Fiber 200 Plus", 32.0, VODAFONE_FIBER_PLUS_DESC));
        vodafone.put(normalizeKey("Vodafone Full Fiber 300 Plus"), new PlanMetadata("Vodafone Full Fiber 300 Plus", 23.0, VODAFONE_FIBER_PLUS_DESC));
        vodafone.put(normalizeKey("Vodafone Full Fiber 500 Plus"), new PlanMetadata("Vodafone Full Fiber 500 Plus", 23.0, VODAFONE_FIBER_PLUS_DESC));
        vodafone.put(normalizeKey("Vodafone Full Fiber 1Gbps Plus"), new PlanMetadata("Vodafone Full Fiber 1Gbps Plus", 26.50, VODAFONE_FIBER_PLUS_DESC));
        VODAFONE_BY_NAME = Map.copyOf(vodafone);
    }

    private PlanCatalog() {}

        public static PlanMetadata lookup(String provider, String rawName) {
                return lookup(provider, rawName, null);
        }

        public static PlanMetadata lookup(String provider, String rawName, Double maxDownloadMbps) {
                if (provider == null || rawName == null) return null;
                String key = normalizeKey(rawName);
                return switch (provider.toUpperCase(Locale.ROOT)) {
                        case "NOVA" -> NOVA_BY_NAME.get(key);
                        case "VODAFONE" -> lookupVodafone(key, maxDownloadMbps);
                        default -> null;
                };
        }

    public static PlanMetadata lookupCosmoteByKey(String key) {
        return key == null ? null : COSMOTE_BY_KEY.get(key);
    }

    public static String normalizeKey(String value) {
        if (value == null) return "";
        String up = value.toUpperCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        String nfd = Normalizer.normalize(up, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }

        private static PlanMetadata lookupVodafone(String normalizedName, Double maxDownloadMbps) {
                PlanMetadata exact = VODAFONE_BY_NAME.get(normalizedName);
                if (exact != null) return exact;

                Double speed = extractSpeed(normalizedName, maxDownloadMbps);
                if (speed == null) return null;

                if (speed <= 30) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone ADSL 24"));
                if (speed <= 70) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone VDSL 50"));
                if (speed <= 150) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone Fiber 100"));
                if (speed <= 250) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone Full Fiber 200 Plus"));
                if (speed <= 400) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone Full Fiber 300 Plus"));
                if (speed <= 700) return VODAFONE_BY_NAME.get(normalizeKey("Vodafone Full Fiber 500 Plus"));
                return VODAFONE_BY_NAME.get(normalizeKey("Vodafone Full Fiber 1Gbps Plus"));
        }

        private static Double extractSpeed(String normalizedName, Double fallback) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2,4})").matcher(normalizedName);
                Double best = null;
                while (matcher.find()) {
                        try {
                                double value = Double.parseDouble(matcher.group(1));
                                if (value >= 24) {
                                        best = value;
                                }
                        } catch (NumberFormatException ignored) {
                        }
                }
                if (best != null) return best;
                return fallback;
        }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> combined = new java.util.ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }
}
