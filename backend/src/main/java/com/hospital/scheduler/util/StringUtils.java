package com.hospital.scheduler.util;

public final class StringUtils {

    private StringUtils() {}

    public static String clean(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.startsWith("=\"") && val.endsWith("\"")) {
            val = val.substring(2, val.length() - 1);
        } else if (val.startsWith("=") && val.length() > 1) {
            val = val.substring(1);
        }
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val.trim();
    }
}
