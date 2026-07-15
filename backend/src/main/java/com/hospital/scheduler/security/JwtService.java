package com.hospital.scheduler.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {


    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Refresh-token expiry in milliseconds (defaults to 7 days).
     * Configured by {@code jwt.refresh-expiration} so ops can shorten it in prod.
     */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /**
     * Claims key for the {@code permVer} (permission-matrix version) integer.
     * Stamped on every token so {@link PermissionInvalidationFilter} can
     * reject stale JWTs after an ADMIN toggles a permission.
     */
    public static final String CLAIM_PERMISSION_VERSION = "permVer";

    private final PermissionVersionService permissionVersionService;

    public JwtService(PermissionVersionService permissionVersionService) {
        this.permissionVersionService = permissionVersionService;
    }

    /**
     * Read the {@code permVer} claim from a token as a {@link Long}. Returns
     * {@code null} when the claim is absent (e.g. legacy tokens issued
     * before RBAC v2 stamped it) so callers can fall back to "force re-auth"
     * rather than silently treating the token as fresh.
     */
    public Long extractPermissionVersion(String token) {
        Claims claims = extractAllClaims(token);
        Object raw = claims.get(CLAIM_PERMISSION_VERSION);
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    /**
     * Stamp {@code permVer} with the current {@code permissions.version}
     * value from {@link PermissionVersionService}. Mutates the supplied map
     * in place so callers can keep their builder fluent.
     */
    private void stampPermissionVersion(Map<String, Object> extraClaims) {
        long version = permissionVersionService.currentVersion();
        extraClaims.put(CLAIM_PERMISSION_VERSION, version);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(String username, List<String> roles) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", roles);
        extraClaims.put("permissions", List.of());
        extraClaims.put("tokenType", "access");
        stampPermissionVersion(extraClaims);
        return generateToken(extraClaims, username);
    }

    /**
     * Build an access token containing both roles and the user's flattened
     * permission set so {@code @PreAuthorize("hasAuthority(...)")} can match
     * without a database round-trip on every request.
     */
    public String generateToken(String username, List<String> roles, List<String> permissions) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", roles != null ? roles : List.of());
        extraClaims.put("permissions", permissions != null ? permissions : List.of());
        extraClaims.put("tokenType", "access");
        stampPermissionVersion(extraClaims);
        return generateToken(extraClaims, username);
    }

    /**
     * Issue a refresh token — same signing key but longer expiry and
     * {@code tokenType=refresh} claim so we can distinguish the two later
     * in {@code JwtAuthenticationFilter}.
     */
    public String generateRefreshToken(String username, List<String> roles, List<String> permissions) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", roles != null ? roles : List.of());
        extraClaims.put("permissions", permissions != null ? permissions : List.of());
        extraClaims.put("tokenType", "refresh");
        stampPermissionVersion(extraClaims);
        return buildToken(extraClaims, username, refreshExpiration);
    }

    public String generateToken(Map<String, Object> extraClaims, String username) {
        return buildToken(extraClaims, username, jwtExpiration);
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    public long getRefreshExpirationTime() {
        return refreshExpiration;
    }

    private String buildToken(Map<String, Object> extraClaims, String username, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Flatten permission set carried in the access token. Older tokens
     * (issued before RBAC v2) do not include this claim — we return an empty
     * list rather than {@code null} so the security filter can treat the
     * principal as having no fine-grained permissions until they re-auth.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Claims claims = extractAllClaims(token);
        Object raw = claims.get("permissions");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Returns the {@code tokenType} claim — either {@code "access"} or
     * {@code "refresh"}. Access tokens have this claim set to {@code "access"}
     * (set in {@link #generateToken}); refresh tokens have {@code "refresh"}.
     * Tokens issued before this field was added default to {@code "access"}.
     */
    public String extractTokenType(String token) {
        Claims claims = extractAllClaims(token);
        Object v = claims.get("tokenType");
        return v != null ? v.toString() : "access";
    }
}
