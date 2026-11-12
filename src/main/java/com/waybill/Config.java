package com.waybill;

public final class Config {
    public static final String REMEMBER_KEY = "waybill-remember-key-2024";
    public static final long REMEMBER_TTL_MS = 14L * 24 * 3600 * 1000;
    public static final String EXPORT_DIR = env("EXPORT_DIR", "/tmp/waybill-exports");

    public static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private Config() {}
}
