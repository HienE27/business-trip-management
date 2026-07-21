package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.dto.response.PageResponse;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.config.ConfigProfile;
import com.hospital.scheduler.scheduling.config.ConfigProfileService;
import com.hospital.scheduler.scheduling.config.ConfigProfileSort;
import com.hospital.scheduler.scheduling.config.dto.ConfigProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PR-11-03 — controller-level contract tests using a hand-rolled stub.
 *
 * <p>We avoid {@code @WebMvcTest} because the project's SecurityFilterChain
 * pulls in many auto-configurations that fight the slice. Instead, this
 * test exercises the controller method directly and asserts that the right
 * {@link Pageable} is built and the right service method is called.
 *
 * <p>The HTTP layer (status codes, JSON shape, security) is covered by the
 * existing GlobalExceptionHandler tests + manual integration tests. This
 * file focuses on the controller's *parameter binding and dispatching*
 * which is what PR-11-03 introduces.
 */
@DisplayName("ConfigProfileController list — pagination + sort dispatch (PR-11-03)")
class ConfigProfileControllerListTest {

    private ConfigProfileService profileService;
    private PaginationConfig paginationConfig;
    private ConfigProfileController controller;

    private ConfigProfileDto sample;

    @BeforeEach
    void setUp() {
        profileService = mock(ConfigProfileService.class);
        paginationConfig = new PaginationConfig();
        controller = new ConfigProfileController(profileService, paginationConfig);

        sample = new ConfigProfileDto(
                1L, "balanced", "Cân bằng", "Balanced", "Default profile",
                ConfigProfile.ProfileCategory.GENERAL, "balance",
                new String[0], true, true, false, null,
                "system", LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("Default params → Pageable(0,20, nameVi ASC)")
    void defaultPagination() {
        when(profileService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample), Pageable.ofSize(20), 1));

        var response = controller.getAll(null, null, null, null, null, null, null, null, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().items()).hasSize(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileService).findAll(captor.capture());
        Pageable p = captor.getValue();
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort().getOrderFor("nameVi")).isNotNull();
    }

    @Test
    @DisplayName("size=500 → capped to 100 by PaginationConfig")
    void oversizedSizeIsCapped() {
        when(profileService.findAll(any(Pageable.class))).thenReturn(Page.empty());

        controller.getAll(null, null, null, null, null, null, null, null, 500, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileService).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("page=2000 → capped to 1000 (maxPageNumber)")
    void pageBeyondMax_capped() {
        when(profileService.findAll(any(Pageable.class))).thenReturn(Page.empty());

        controller.getAll(null, null, null, null, null, null, null, 2000, 20, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileService).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1000);
    }

    @Test
    @DisplayName("sort=updatedAt,desc → Pageable sort applied")
    void sortWhitelistAccepted() {
        when(profileService.findAll(any(Pageable.class))).thenReturn(Page.empty());

        controller.getAll(null, null, null, null, null, null, null, null, 20, "updatedAt,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileService).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("updatedAt"))
                .isNotNull()
                .satisfies(o -> assertThat(o.getDirection().isDescending()).isTrue());
    }

    @Test
    @DisplayName("sort=password,asc → BadRequestException (controller propagates)")
    void sortFieldNotInWhitelist_throws() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> controller.getAll(null, null, null, null, null, null, null, null, 20, "password,asc"))
                .isInstanceOf(com.hospital.scheduler.exception.BadRequestException.class)
                .hasMessageContaining("password");
    }

    @Nested
    @DisplayName("Filter routing")
    class FilterRouting {

        @Test
        void systemOnly_routesToFindSystem() {
            when(profileService.findSystemProfiles(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(sample), Pageable.ofSize(20), 1));

            controller.getAll(null, true, null, null, null, null, null, null, null, null);

            verify(profileService).findSystemProfiles(any(Pageable.class));
        }

        @Test
        void customOnly_routesToFindCustom() {
            when(profileService.findCustomProfiles(any(Pageable.class)))
                    .thenReturn(Page.empty());

            controller.getAll(null, null, true, null, null, null, null, null, null, null);

            verify(profileService).findCustomProfiles(any(Pageable.class));
        }

        @Test
        void favoritesOnly_routesToFindFavorites() {
            when(profileService.findFavorites(any(Pageable.class)))
                    .thenReturn(Page.empty());

            controller.getAll(null, null, null, true, null, null, null, null, null, null);

            verify(profileService).findFavorites(any(Pageable.class));
        }

        @Test
        void search_routesToSearchPaged() {
            when(profileService.search(eq("foo"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            controller.getAll(null, null, null, null, null, null, "foo", null, null, null);

            verify(profileService).search(eq("foo"), any(Pageable.class));
        }

        @Test
        void category_routesToFindByCategory() {
            when(profileService.findByCategory(eq(ConfigProfile.ProfileCategory.ALGORITHM), any(Pageable.class)))
                    .thenReturn(Page.empty());

            controller.getAll("ALGORITHM", null, null, null, null, null, null, null, null, null);

            verify(profileService).findByCategory(eq(ConfigProfile.ProfileCategory.ALGORITHM), any(Pageable.class));
        }

        @Test
        void invalidCategory_throwsIllegalArgument() {
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> controller.getAll("BOGUS", null, null, null, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("PageResponse is wrapped in ApiResponse with success=true")
    void responseEnvelope() {
        Pageable sorted = PageRequest.of(0, 20, Sort.by("nameVi"));
        when(profileService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample), sorted, 1));

        var response = controller.getAll(null, null, null, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
        PageResponse<ConfigProfileDto> data = response.getBody().getData();
        assertThat(data.items()).hasSize(1);
        assertThat(data.page()).isEqualTo(0);
        assertThat(data.size()).isEqualTo(20);
        assertThat(data.totalItems()).isEqualTo(1);
        assertThat(data.sort()).contains("nameVi");
    }
}