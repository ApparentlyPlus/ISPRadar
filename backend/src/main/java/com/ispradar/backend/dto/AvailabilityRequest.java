package com.ispradar.backend.dto;

/*
 * The frontend sends back the full AddressItems it received from each step.
 * number — free-text input (used by Cosmote directly)
 * numberItem  — selected from the Vodafone numbers dropdown (carries vodafoneCtx)
 */
public record AvailabilityRequest(
        AddressItem state,
        AddressItem municipality,
        AddressItem postalCode,    // null for Cosmote-only addresses
        AddressItem street,
        AddressItem area,          // null for Vodafone-only addresses
        String number,
        AddressItem numberItem     // null if user didn't pick from Vodafone dropdown
) {}