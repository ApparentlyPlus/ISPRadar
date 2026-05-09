package com.ispradar.backend.dto;

import java.util.Map;

/*
 * A (kinda) unified address selection item.
 * Each provider's opaque context is stored in its respective field.
 * Null means the provider has no data for this item at this step.
 */
public record AddressItem(
        String label,
        Map<String, Object> cosmoteCtx,
        Map<String, Object> vodafoneCtx
) {}