package com.ispradar.backend.dto;

import java.util.List;

public record AvailabilityResponse(
        String address,
        List<Plan> plans,
        List<String> errors // non-fatal per-provider errors
) {}