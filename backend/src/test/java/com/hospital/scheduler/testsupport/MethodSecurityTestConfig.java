package com.hospital.scheduler.testsupport;

import com.hospital.scheduler.security.Permissions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal method-security config for {@code @WebMvcTest} slices.
 *
 * <p>{@code @WebMvcTest} auto-registers a default
 * {@link org.springframework.security.web.FilterChainProxy} from
 * {@link org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration},
 * but it does <b>NOT</b> activate {@code @PreAuthorize} / {@code @PostAuthorize}
 * unless {@code @EnableMethodSecurity} is present in the test context.
 *
 * <p>Importing this config into a {@code @WebMvcTest} test class activates
 * method-level security so that controller methods annotated with
 * {@code @PreAuthorize("hasAuthority('...')")} actually enforce their rules.
 *
 * <p>The chain permits all — URL-level auth is tested separately in
 * integration tests. Method-level auth is what this config is for.
 */
@TestConfiguration
@EnableMethodSecurity
public class MethodSecurityTestConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    com.hospital.scheduler.security.AuthContextService authContextService() {
        com.hospital.scheduler.security.AuthContextService mock = org.mockito.Mockito.mock(com.hospital.scheduler.security.AuthContextService.class);
        org.mockito.Mockito.lenient().when(mock.isCurrentStaff(org.mockito.Mockito.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(mock.isCurrentStaffOwnerOfLeaveRequest(org.mockito.Mockito.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(mock.isCurrentStaffOwnerOfExchange(org.mockito.Mockito.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(mock.isCurrentStaffOwner(org.mockito.Mockito.any())).thenReturn(true);
        return mock;
    }
}
