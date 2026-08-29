package com.razorpay.recovery.controller;

import com.razorpay.recovery.config.BoundsConfig;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Exposes the RulesEngine's hard bounds for live editing at runtime.
 * GET returns the current values; PUT applies new values that take effect
 * on the NEXT batch run — never bypasses enforceBounds().
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
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
        boundsConfig.apply(
                request.maxRetries(),
                request.maxDiscountPercent(),
                request.minAmountForDiscount(),
                request.retryCooldownMinutes()
        );
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
            Integer retryCooldownMinutes
    ) {}
}
