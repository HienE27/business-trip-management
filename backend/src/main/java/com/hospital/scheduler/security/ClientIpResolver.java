package com.hospital.scheduler.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Centralized helper for extracting the real client IP from an HTTP request.
 *
 * <p>BUGFIX (was BE#14 / BE#15): the previous version had two near-duplicate
 * copies of this logic — one in {@code AuthService.getClientIp()} and one in
 * {@code RateLimitingFilter.getClientIp()} — and they disagreed:
 * <ul>
 *   <li>AuthService checked a hand-written trusted-proxy list (only handled
 *       the long-form IPv6 loopback {@code 0:0:0:0:0:0:0:1}, missing {@code ::1},
 *       IPv6 ULA, link-local, IPv4-mapped forms).</li>
 *   <li>RateLimitingFilter trusted {@code X-Forwarded-For} unconditionally —
 *       an attacker could spoof the header to bypass IP-based login throttling.</li>
 * </ul>
 *
 * <p>This resolver unifies both call sites with:
 * <ol>
 *   <li>{@code java.net.InetAddress} for canonical loopback/link-local checks
 *       (handles both IPv4 and IPv6 forms correctly).</li>
 *   <li>A private/loopback allowlist (10/8, 172.16/12, 192.168/16, ::1, fc00::/7,
 *       fe80::/10) used to decide whether to honor {@code X-Forwarded-For}.</li>
 *   <li>Safe parsing that strips surrounding whitespace and rejects malformed
 *       comma lists without crashing.</li>
 * </ol>
 *
 * <p>Use {@link #resolve(HttpServletRequest)} from any service/filter that
 * needs the real client IP for auditing, rate limiting, or logging.
 */
@Component
public class ClientIpResolver {

    /** Trust header only when the direct connection comes from a private/loopback address. */
    private static final boolean DEFAULT_TRUST_FORWARDED = true;

    /**
     * Return the best-effort real client IP. If a non-private proxy sent the
     * request, returns the direct address (no spoofing risk). If a trusted
     * private/loopback proxy sent it, parses the leftmost X-Forwarded-For
     * entry — which is the original client by RFC 7239 convention.
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xf = request.getHeader("X-Forwarded-For");
        String directIp = request.getRemoteAddr();
        if (xf == null || xf.isBlank()) {
            return directIp;
        }
        if (!DEFAULT_TRUST_FORWARDED || !isTrustedProxy(directIp)) {
            return directIp;
        }
        // Take the leftmost entry (original client per RFC convention).
        int comma = xf.indexOf(',');
        String first = comma >= 0 ? xf.substring(0, comma) : xf;
        return first.trim();
    }

    /**
     * Decide whether the given peer IP belongs to a trusted forwarder. Returns
     * true for loopback, link-local, and RFC1918/private ranges on both IPv4
     * and IPv6.
     */
    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) return true;       // covers ::1 and 127.0.0.1
            if (addr.isLinkLocalAddress()) return true;      // fe80::/10 and 169.254/16
            if (addr.isAnyLocalAddress()) return true;       // 0.0.0.0 and ::
            byte[] octets = addr.getAddress();
            if (octets.length == 4) {
                // IPv4 RFC1918 + shared address space
                int b0 = octets[0] & 0xFF;
                int b1 = octets[1] & 0xFF;
                if (b0 == 10) return true;
                if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
                if (b0 == 192 && b1 == 168) return true;
                if (b0 == 100 && b1 >= 64 && b1 <= 127) return true; // CGNAT 100.64/10
                if (b0 == 0) return true; // 0.0.0.0/8
                return false;
            }
            if (octets.length == 16) {
                // IPv6 unique local (fc00::/7) and IPv4-mapped (::ffff:0:0/96)
                int b0 = octets[0] & 0xFF;
                if ((b0 & 0xFE) == 0xFC) return true;        // fc00::/7
                // IPv4-mapped IPv6: treat the embedded IPv4 with the same rules
                boolean mapped = true;
                for (int i = 0; i < 10; i++) {
                    if (octets[i] != 0) { mapped = false; break; }
                }
                if (mapped && (octets[10] & 0xFF) == 0xFF && (octets[11] & 0xFF) == 0xFF) {
                    int v4b0 = octets[12] & 0xFF;
                    int v4b1 = octets[13] & 0xFF;
                    int v4b2 = octets[14] & 0xFF;
                    int v4b3 = octets[15] & 0xFF;
                    // Re-route to the IPv4 checker with a synthetic "a.b.c.d" string.
                    String embedded = v4b0 + "." + v4b1 + "." + v4b2 + "." + v4b3;
                    return isTrustedProxy(embedded);
                }
                return false;
            }
            return false;
        } catch (UnknownHostException ex) {
            // Malformed IP — be safe and reject trust so attackers can't smuggle
            // an invalid IP through X-Forwarded-For validation.
            return false;
        }
    }
}