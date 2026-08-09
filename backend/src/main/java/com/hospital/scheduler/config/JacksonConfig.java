package com.hospital.scheduler.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson {@link ObjectMapper} configuration for the application.
 *
 * <p>Spring Boot auto-configures an {@code ObjectMapper} with {@code JavaTimeModule}
 * and {@code WRITE_DATES_AS_TIMESTAMPS = false}.  This bean applies two additional
 * changes on top of the auto-configured mapper:
 *
 * <ul>
 *   <li>{@code setSerializationInclusion(JsonInclude.Include.ALWAYS)} — Jackson's
 *       default is {@code NON_NULL} (skip null fields).  {@code ALWAYS} ensures
 *       that fields serialised as {@code null} are still emitted in the JSON
 *       response, which makes the API surface more predictable for consumers
 *       (every field appears in every response, even when {@code null}).</li>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — Jackson's raw default is
 *       {@code true} (throw {@code UnrecognizedPropertyException} when the client
 *       sends a field that the DTO doesn't know about).  Spring Boot normally
 *       overrides this to {@code false} on its auto-configured mapper, but since
 *       this bean replaces the default with a raw {@code new ObjectMapper()}, we
 *       have to disable the flag explicitly.  Without it, endpoints like
 *       {@code PUT /auto-schedule/runtime-config} return HTTP 400 whenever the
 *       frontend sends extra fields (e.g. {@code holidayMode},
 *       {@code removedShiftTypes}) that the backend
 *       DTO doesn't declare — the front and back ends drift apart and the save
 *       button mysteriously fails.</li>
 * </ul>
 *
 * <p>Changing {@code ALWAYS} to {@code NON_NULL} would reduce payload size but
 * would be a breaking change for existing API consumers.  If payload size is a
 * concern, migrate field-by-field with {@code @JsonInclude(NON_NULL)} annotations
 * instead of changing the global default.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // BUGFIX (was STATS-1): enable USE_STD_BEAN_NAMING so that fields with
        // explicit @JsonProperty annotations (e.g. L01Count, L02Count in
        // StaffShiftStatistics) are emitted ONLY under the annotated name.
        // Without this flag — and because bean name L01Count also lowercases to
        // l01Count via Jackson's default name translation — every field was
        // serialised twice (l01Count AND L01Count), producing invalid JSON that
        // PowerShell ConvertFrom-Json rejects with "duplicated keys" and that
        // strict OpenAPI consumers (Swagger UI, codegen) fail to render.
        mapper.enable(MapperFeature.USE_STD_BEAN_NAMING);
        // BUGFIX: accept unknown properties so frontend can evolve its payload
        // shape without forcing a coordinated backend redeploy for every UI tweak.
        // Server-side validation is the source of truth; client-side extras are noise.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // @formatter:off
        // Intentionally ALWAYS — see class-level javadoc for rationale.
        // @formatter:on
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return mapper;
    }
}
