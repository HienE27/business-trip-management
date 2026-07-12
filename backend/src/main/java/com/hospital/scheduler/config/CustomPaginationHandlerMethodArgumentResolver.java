package com.hospital.scheduler.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom argument resolver that intercepts every {@link Pageable} request parameter
 * and applies the safe limits defined in {@link PaginationConfig}.
 *
 * Fixes:
 * - BUG-m1: size exceeding max (e.g. size=999999) → capped to maxPageSize
 * - BUG-m2: negative page number → coerced to 0
 * - BUG-m3: size=0 returns empty list instead of error → coerced to default
 *
 * Registered via {@code WebMvcConfigurer#addArgumentResolvers}, replacing the
 * default {@link PageableHandlerMethodArgumentResolver} for this application.
 */
@Component
public class CustomPaginationHandlerMethodArgumentResolver extends PageableHandlerMethodArgumentResolver {

    private final PaginationConfig paginationConfig;

    @Autowired
    public CustomPaginationHandlerMethodArgumentResolver(PaginationConfig paginationConfig) {
        this.paginationConfig = paginationConfig;
    }

    @Override
    public Pageable resolveArgument(
            MethodParameter methodParameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Pageable delegate = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        int page = delegate.getPageNumber();
        int size = delegate.getPageSize();

        // BUG-m2: negative page → 0
        int safePage = Math.max(0, page);

        // BUG-m3: size=0 → defaultPageSize
        // BUG-m1: size > maxPageSize → maxPageSize
        int safeSize = size <= 0
                ? paginationConfig.getDefaultPageSize()
                : Math.min(size, paginationConfig.getMaxPageSize());

        return PageRequest.of(safePage, safeSize, delegate.getSort());
    }
}
