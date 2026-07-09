package com.hospital.scheduler.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * WebMvcConfigurer that registers the custom {@link CustomPaginationHandlerMethodArgumentResolver}.
 *
 * The custom resolver replaces Spring's default Pageable resolution with a guarded version
 * that applies safe limits from {@link PaginationConfig} to all paginated endpoints.
 *
 * @see CustomPaginationHandlerMethodArgumentResolver
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CustomPaginationHandlerMethodArgumentResolver paginationResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Replace the default PageableHandlerMethodArgumentResolver with our guarded version.
        // Remove any existing Pageable resolvers first to avoid duplicates.
        resolvers.removeIf(r -> r instanceof org.springframework.data.web.PageableHandlerMethodArgumentResolver);
        resolvers.add(paginationResolver);
    }
}
