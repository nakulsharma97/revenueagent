package com.razorpay.recovery.config;

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
        if (request.language() != null) {
            boundsConfig.setLanguage(request.language());
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
            String language
    ) {}
}
