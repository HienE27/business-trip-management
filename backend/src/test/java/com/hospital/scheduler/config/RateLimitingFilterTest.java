package com.hospital.scheduler.config;

import com.hospital.scheduler.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimitingFilter} — focused on BE#16 (path-normalization
 * bypass).
 *
 * <p>Bug summary: the previous implementation matched the URI with an exact
 * {@code String.equals("/api/v1/auth/login")}. Spring's DispatcherServlet
 * routes alternate spellings like {@code /api/v1/auth//login} and
 * {@code /api/v1/auth/login;jsessionid=foo} to the same
 * {@link com.hospital.scheduler.controller.AuthController#login} handler, so
 * an attacker could reset the IP-based counter by toggling the path after
 * tripping the threshold. These tests pin the helper's normalization so the
 * bypass stays closed.
 */
@DisplayName("RateLimitingFilter - path normalization (BE#16)")
class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private RecordingFilterChain chain;

    @BeforeEach
    void setUp() {
        ClientIpResolver ipResolver = new ClientIpResolver();
        filter = new RateLimitingFilter(ipResolver);
        filter.rateLimitEnabled = true;
        filter.maxAttempts = 2;     // tight threshold so tests run quickly
        filter.windowMinutes = 15;
        chain = new RecordingFilterChain();
    }

    @Nested
    @DisplayName("Canonical path still recognized")
    class CanonicalPath {

        @Test
        void exact_match_triggersFilter_andChainsThrough() throws Exception {
            MockHttpServletRequest req = post("/api/v1/auth/login", "127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);

            // Filter passes through on the first request — chain invoked,
            // status untouched.
            assertThat(chain.invoked).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("BE#16: alternate paths must also be rate-limited")
    class BypassAttempts {

        @Test
        void doubleSlash_betweenSegments_stillTriggersFilter() throws Exception {
            // Spring still routes /api/v1/auth//login to AuthController.login.
            MockHttpServletRequest req = post("/api/v1/auth//login", "127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);
            assertThat(chain.invoked).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }

        @Test
        void pathParam_afterLogin_stillTriggersFilter() throws Exception {
            // Spring strips ;jsessionid=foo before route resolution.
            MockHttpServletRequest req = post("/api/v1/auth/login;jsessionid=foo", "127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);
            assertThat(chain.invoked).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }

        @Test
        void doubleSlash_and_pathParam_combined_stillTriggersFilter() throws Exception {
            MockHttpServletRequest req = post("/api/v1/auth//login;x=y", "127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);
            assertThat(chain.invoked).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("BE#16: rate limit applies across normalized paths")
    class RateLimitAppliesAcrossPaths {

        @Test
        void twoFailuresOnCanonical_thenSameIpOnDoubleSlash_isBlocked() throws Exception {
            // Seed the per-IP bucket the way AuthService does after a
            // failed-password result (recordFailedLogin), then verify the
            // filter blocks even when the request comes through a
            // path-bypass.
            filter.recordFailedLogin("10.0.0.5");
            filter.recordFailedLogin("10.0.0.5");

            MockHttpServletResponse res = new MockHttpServletResponse();
            chain.invoked = false;

            filter.doFilter(post("/api/v1/auth//login", "10.0.0.5"), res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(chain.invoked).as("filter must short-circuit; controller never reached").isFalse();
            assertThat(res.getContentAsString()).contains("Quá nhiều yêu cầu đăng nhập");
        }

        @Test
        void twoFailuresOnPathParam_thenSameIpOnCanonical_isBlocked() throws Exception {
            filter.recordFailedLogin("10.0.0.6");
            filter.recordFailedLogin("10.0.0.6");

            MockHttpServletResponse res = new MockHttpServletResponse();
            chain.invoked = false;

            filter.doFilter(post("/api/v1/auth/login", "10.0.0.6"), res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(chain.invoked).isFalse();
        }
    }

    @Nested
    @DisplayName("Non-login paths remain unfiltered")
    class OtherPaths {

        @Test
        void differentIp_neverHitsFilter() throws Exception {
            MockHttpServletRequest req = post("/api/v1/something/else", "127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);
            assertThat(chain.invoked).isTrue();
        }

        @Test
        void getOnLoginPath_doesNotTriggerFilter() throws Exception {
            // GET /api/v1/auth/login isn't even a real route, but the filter
            // must not rate-limit it (only POST does password-checking work).
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/login");
            req.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, chain);
            assertThat(chain.invoked).isTrue();
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static MockHttpServletRequest post(String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    /** Bare-bones chain that just records whether it ran. */
    private static final class RecordingFilterChain implements FilterChain {
        boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}
