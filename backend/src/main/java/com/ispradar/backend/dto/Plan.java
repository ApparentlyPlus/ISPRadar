package com.ispradar.backend.dto;

public record Plan(
        String provider, // COSMOTE, VODAFONE, NOVA
        String name,     // plan name, like "COSMOTE 100 Mbps"
        Double maxDownloadMbps,
        Double maxUploadMbps,
        Double price,
        java.util.List<String> description
) {}