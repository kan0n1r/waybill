package com.waybill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class Pricing {
    private static final BigDecimal BASE = new BigDecimal("4.50");
    private static final Map<String, BigDecimal> RATE = Map.of(
            "economy", new BigDecimal("1.20"),
            "standard", new BigDecimal("1.80"),
            "express", new BigDecimal("3.20"));

    public static boolean validService(String service) {
        return RATE.containsKey(service);
    }

    public static BigDecimal price(BigDecimal weightKg, String service) {
        return BASE.add(weightKg.multiply(RATE.get(service))).setScale(2, RoundingMode.HALF_UP);
    }

    private Pricing() {}
}
