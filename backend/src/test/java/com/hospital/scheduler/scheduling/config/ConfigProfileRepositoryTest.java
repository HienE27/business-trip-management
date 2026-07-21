package com.hospital.scheduler.scheduling.config;

import com.hospital.scheduler.scheduling.config.ConfigProfile.ProfileCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR-11-01 — Persistence layer tests for {@link ConfigProfileRepository}.
 *
 * <p>Scope: verify JPA mappings and derived/custom queries. No business logic —
 * that's covered by {@code ConfigProfileServiceTest} in a later PR.
 *
 * <p>Uses embedded H2 (replace MySQL config via {@code @AutoConfigureTestDatabase})
 * so the test does not require a running MySQL instance.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@DisplayName("ConfigProfileRepository — persistence layer")
class ConfigProfileRepositoryTest {

    @Autowired
    private ConfigProfileRepository repository;

    private ConfigProfile systemProfile;
    private ConfigProfile customProfile;
    private ConfigProfile defaultProfile;
    private ConfigProfile favoriteProfile;

    @BeforeEach
    void seed() {
        repository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        systemProfile = ConfigProfile.builder()
                .profileKey("balanced")
                .nameVi("Cân bằng")
                .nameEn("Balanced")
                .description("System default")
                .category(ProfileCategory.GENERAL)
                .icon("balance")
                .isSystem(true)
                .isDefault(false)
                .isFavorite(false)
                .configJson("{\"enabled\":true}")
                .createdBy("system")
                .createdAt(now)
                .build();

        defaultProfile = ConfigProfile.builder()
                .profileKey("balanced-default")
                .nameVi("Cân bằng (default)")
                .nameEn("Balanced Default")
                .description("Currently active default")
                .category(ProfileCategory.GENERAL)
                .icon("balance")
                .isSystem(true)
                .isDefault(true)
                .isFavorite(false)
                .configJson("{\"enabled\":true}")
                .createdBy("system")
                .createdAt(now)
                .build();

        favoriteProfile = ConfigProfile.builder()
                .profileKey("emergency")
                .nameVi("Khẩn cấp")
                .nameEn("Emergency")
                .category(ProfileCategory.EMERGENCY)
                .icon("emergency")
                .isSystem(true)
                .isDefault(false)
                .isFavorite(true)
                .configJson("{\"timeLimitSeconds\":30}")
                .createdBy("system")
                .createdAt(now)
                .build();

        customProfile = ConfigProfile.builder()
                .profileKey("icu-custom")
                .nameVi("ICU Tùy chỉnh")
                .nameEn("ICU Custom")
                .description("Per-ICU tuning")
                .category(ProfileCategory.COVERAGE)
                .icon("stethoscope")
                .tags(new String[]{"icu", "production"})
                .isSystem(false)
                .isDefault(false)
                .isFavorite(false)
                .configJson("{\"maxShiftsPerStaff\":10}")
                .createdBy("admin")
                .createdAt(now)
                .build();

        repository.saveAll(List.of(systemProfile, defaultProfile, favoriteProfile, customProfile));
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        @DisplayName("save() persists and assigns ID")
        void save_persists() {
            ConfigProfile p = ConfigProfile.builder()
                    .profileKey("tmp")
                    .nameVi("Tmp")
                    .category(ProfileCategory.TESTING)
                    .isSystem(false)
                    .isDefault(false)
                    .isFavorite(false)
                    .configJson("{}")
                    .createdBy("admin")
                    .createdAt(LocalDateTime.now())
                    .build();

            ConfigProfile saved = repository.save(p);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getProfileKey()).isEqualTo("tmp");
        }

        @Test
        @DisplayName("findById() returns entity")
        void findById_returnsEntity() {
            Optional<ConfigProfile> found = repository.findById(systemProfile.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getNameVi()).isEqualTo("Cân bằng");
        }

        @Test
        @DisplayName("delete() removes the row")
        void delete_removes() {
            long before = repository.count();
            repository.delete(customProfile);
            repository.flush();
            assertThat(repository.count()).isEqualTo(before - 1);
        }
    }

    @Nested
    @DisplayName("findByProfileKey")
    class FindByKey {

        @Test
        @DisplayName("existing key → entity")
        void existingKey_returnsEntity() {
            Optional<ConfigProfile> found = repository.findByProfileKey("icu-custom");
            assertThat(found).isPresent();
            assertThat(found.get().getCreatedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("non-existing key → empty Optional")
        void missingKey_returnsEmpty() {
            Optional<ConfigProfile> found = repository.findByProfileKey("does-not-exist");
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByProfileKey")
    class ExistsByKey {

        @Test
        @DisplayName("true for existing key")
        void existing_returnsTrue() {
            assertThat(repository.existsByProfileKey("balanced")).isTrue();
        }

        @Test
        @DisplayName("false for missing key")
        void missing_returnsFalse() {
            assertThat(repository.existsByProfileKey("nope")).isFalse();
        }
    }

    @Nested
    @DisplayName("System vs custom filter")
    class SystemFilter {

        @Test
        @DisplayName("findByIsSystemTrue returns 3 system profiles")
        void system_returnsAllSystem() {
            List<ConfigProfile> sys = repository.findByIsSystemTrue();
            assertThat(sys).hasSize(3)
                    .extracting(ConfigProfile::getProfileKey)
                    .containsExactlyInAnyOrder("balanced", "balanced-default", "emergency");
        }

        @Test
        @DisplayName("findByIsSystemFalse returns 1 custom profile")
        void custom_returnsCustomOnly() {
            List<ConfigProfile> custom = repository.findByIsSystemFalse();
            assertThat(custom).hasSize(1)
                    .extracting(ConfigProfile::getProfileKey)
                    .containsExactly("icu-custom");
        }
    }

    @Nested
    @DisplayName("Category filter")
    class CategoryFilter {

        @Test
        @DisplayName("findByCategory(GENERAL) returns 2")
        void general_returnsTwo() {
            List<ConfigProfile> general = repository.findByCategory(ProfileCategory.GENERAL);
            assertThat(general).hasSize(2);
        }

        @Test
        @DisplayName("findByCategory(EMERGENCY) returns 1")
        void emergency_returnsOne() {
            List<ConfigProfile> emergency = repository.findByCategory(ProfileCategory.EMERGENCY);
            assertThat(emergency).hasSize(1)
                    .extracting(ConfigProfile::getProfileKey)
                    .containsExactly("emergency");
        }
    }

    @Nested
    @DisplayName("Default flag")
    class DefaultFlag {

        @Test
        @DisplayName("findByIsDefaultTrue returns the only default profile")
        void findDefault_returnsOne() {
            Optional<ConfigProfile> def = repository.findByIsDefaultTrue();
            assertThat(def).isPresent();
            assertThat(def.get().getProfileKey()).isEqualTo("balanced-default");
        }

        @Test
        @DisplayName("clearAllDefaults() flips isDefault=false for all")
        void clearAllDefaults_resetsAllFlags() {
            repository.clearAllDefaults();
            repository.flush();

            Optional<ConfigProfile> def = repository.findByIsDefaultTrue();
            assertThat(def).isEmpty();

            // Reload original to confirm flag flipped
            ConfigProfile reloaded = repository.findById(defaultProfile.getId()).orElseThrow();
            assertThat(reloaded.isDefault()).isFalse();
        }
    }

    @Nested
    @DisplayName("Favorites filter")
    class FavoritesFilter {

        @Test
        @DisplayName("findByIsFavoriteTrue returns 1 favorite profile")
        void favorites_returnsOne() {
            List<ConfigProfile> favs = repository.findByIsFavoriteTrue();
            assertThat(favs).hasSize(1)
                    .extracting(ConfigProfile::getProfileKey)
                    .containsExactly("emergency");
        }

        @Test
        @DisplayName("clearAllFavorites() flips all isFavorite=false")
        void clearAllFavorites_resetsAllFlags() {
            repository.clearAllFavorites();
            repository.flush();

            assertThat(repository.findByIsFavoriteTrue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Search by name")
    class SearchByName {

        @Test
        @DisplayName("case-insensitive partial match on name_vi")
        void searchVi_matchesCaseInsensitive() {
            List<ConfigProfile> results = repository.searchByName("cân");
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("case-insensitive partial match on name_en")
        void searchEn_matchesCaseInsensitive() {
            List<ConfigProfile> results = repository.searchByName("emergency");
            assertThat(results).hasSize(1)
                    .extracting(ConfigProfile::getProfileKey)
                    .containsExactly("emergency");
        }

        @Test
        @DisplayName("no match → empty list")
        void noMatch_returnsEmpty() {
            List<ConfigProfile> results = repository.searchByName("xyz-no-match");
            assertThat(results).isEmpty();
        }
    }
}
