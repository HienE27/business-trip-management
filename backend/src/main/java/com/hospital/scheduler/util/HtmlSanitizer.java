package com.hospital.scheduler.util;

public class HtmlSanitizer {

    public static String sanitize(String input) {
        if (input == null) return null;
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    public static String sanitizeAndTrim(String input) {
        if (input == null) return null;
        return sanitize(input).trim();
    }
}
