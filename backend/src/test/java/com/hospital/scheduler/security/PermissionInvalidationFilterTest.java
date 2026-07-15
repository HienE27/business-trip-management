package com.hospital.scheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural test for {@link PermissionInvalidationFilter} — the bouncer
 * that rejects stale JWTs after a permission matrix change.
 *
 * <p>What we cover:
 * <ol>
 *   <li><b>Token version >= DB version</b> → pass through (filter chain called).</li>
 *   <li><b>Token version < DB version</b> → 401 + body with
 *       {@code code: "PERMISSION_VERSION_STALE"} + header {@code X-Permission-Version}.</li>
 *   <li><b>Anonymous (no auth)</b> → pass through (no spurious 401s on /login).</li>
 *   <li><b>Token missing {@code permVer} claim</b> → still rejected, because the
 *       system cannot verify whether the token is up to date.</li>
 *   <li><b>Whitelisted paths (auth/actuator/swagger)</b> → pass through even
 *       when stale, otherwise users can never re-authenticate.</li>
 * </ol>
 *
 * <p>This is the contract the frontend {@code api-client} relies on to decide
 * between "attempt refresh" and "force re-login". If you change the response
 * shape, update {@code api-client.ts#request} correspondingly.
 */
@ExtendWith(MockitoExtension.class)
class PermissionInvalidationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private PermissionVersionService versionService;
    @Mock private FilterChain chain;

    @InjectMocks private PermissionInvalidationFilter filter;

    private static final String STALE_TOKEN = "stale-token-value";
    private static final long NOW_EPOCH = 1_700_000_000_000L;
    private static final long OLD_EPOCH = 1_600_000_000_000L;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThrough_whenTokenVersionMatchesCurrent() throws Exception {
        installTokenInContext(STALE_TOKEN);
        when(jwtService.extractPermissionVersion(STALE_TOKEN)).thenReturn(NOW_EPOCH);
        when(versionService.currentVersion()).thenReturn(NOW_EPOCH);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/staff");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void passesThrough_whenTokenVersionIsNewerThanCurrent() throws Exception {
        // The AtomicLong-backed version service guarantees bump() always
        // strictly increases, so a token stamped after the last bump must
        // pass even by a single millisecond.
        installTokenInContext(STALE_TOKEN);
        when(jwtService.extractPermissionVersion(STALE_TOKEN)).thenReturn(NOW_EPOCH + 5L);
        when(versionService.currentVersion()).thenReturn(NOW_EPOCH);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/staff");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void rejects401_whenTokenVersionIsOlderThanDbVersion() throws Exception {
        installTokenInContext(STALE_TOKEN);
        when(jwtService.extractPermissionVersion(STALE_TOKEN)).thenReturn(OLD_EPOCH);
        when(versionService.currentVersion()).thenReturn(NOW_EPOCH);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/staff");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString())
                .contains("PERMISSION_VERSION_STALE")
                .contains("Permission matrix has changed");
        assertThat(res.getHeader("X-Permission-Version"))
                .isEqualTo(String.valueOf(NOW_EPOCH));
    }

    @Test
    void rejects401_whenTokenHasNoPermVerClaim() throws Exception {
        installTokenInContext(STALE_TOKEN);
        when(jwtService.extractPermissionVersion(STALE_TOKEN)).thenReturn(null);
        // Even with current version, null claim means "token issued before this
        // feature shipped" → conservative: force re-auth.
        when(versionService.currentVersion()).thenReturn(NOW_EPOCH);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/staff");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void passesThrough_whenAnonymous() throws Exception {
        // No authentication in context → filter should not interfere.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/staff");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        verifyNoInteractions(jwtService, versionService);
    }

    @Test
    void whitelistSkipsFilter_onAuthEndpoints() throws Exception {
        // Whitelist check happens BEFORE the auth check, so even with a
        // stale token in the context the filter must let the request through.
        installTokenInContext(STALE_TOKEN);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        // Note: jwtService.extractPermissionVersion IS called because shouldNotFilter
        // is only evaluated by the framework, and we're invoking doFilterInternal
        // directly here. We just assert no 401 was written.
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void whitelistSkipsFilter_onSwaggerAndActuator() throws Exception {
        installTokenInContext(STALE_TOKEN);

        for (String path : new String[] {
                "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html",
                "/actuator/health",
                "/error"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            verify(chain).doFilter(req, res);
            assertThat(res.getStatus()).as("path=%s", path).isEqualTo(200);
        }
    }

    /**
     * Mimics what {@code JwtAuthenticationFilter} does: stash the raw JWT
     * string as the principal so this filter can extract claims from it.
     * The actual filter chain wiring is exercised by the Spring tests under
     * the {@code /integration} package — here we only need to verify the
     * "stale → 401" path.
     */
    private void installTokenInContext(String token) {
        // Mimic what JwtAuthenticationFilter does in production: principal is
        // the username, the raw JWT lives in the details payload so this
        // filter can re-extract the permVer claim.
        Authentication auth = mock(Authentication.class, withSettings().lenient());
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        // Production contract: principal = username (NOT the JWT). Tests
        // verify the filter doesn't blindly cast principal to a JWT anymore.
        lenient().when(auth.getPrincipal()).thenReturn("admin");
        lenient().when(auth.getDetails()).thenReturn(new JwtAuthenticationDetails(token, new MockHttpServletRequest()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}