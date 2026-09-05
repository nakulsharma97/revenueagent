package com.razorpay.recovery.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Exposes the RulesEngine's hard bounds for live editing at runtime.
 * GET returns the current values; PUT applies new values that take effect
 * on the NEXT batch run — never bypasses enforceBounds().
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final BoundsConfig boundsConfig;

    public ConfigController(BoundsConfig boundsConfig) {
        this.boundsConfig = boundsConfig;
    }

    @GetMapping("/bounds")
    public BoundsConfig.BoundsSnapshot getBounds() {
        return boundsConfig.snapshot();
    }

    @PutMapping("/bounds")
    public BoundsConfig.BoundsSnapshot updateBounds(@RequestBody BoundsUpdateRequest request) {
        try {
            boundsConfig.apply(
                    request.maxRetries(),
                request.maxDiscountPercent(),
                request.minAmountForDiscount(),
                request.retryCooldownMinutes(),
                request.hvMaxRetries(),
                request.hvMaxDiscountPercent(),
                    request.hvMinAmountForDiscount(),
                    request.language()
            );
        } catch (IllegalArgumentException e) {
            // An out-of-range edit must fail loudly, not be silently ignored.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return boundsConfig.snapshot();
    }

    /**
     * Partial-update request body — all fields optional.
     * Only non-null fields are applied.
     */
    public record BoundsUpdateRequest(
            Integer maxRetries,
            Integer maxDiscountPercent,
            BigDecimal minAmountForDiscount,
            Integer retryCooldownMinutes,
            String language,
            Integer hvMaxRetries,
            Integer hvMaxDiscountPercent,
            BigDecimal hvMinAmountForDiscount
    ) {}
}
