package com.hospital.scheduler.config;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * </ul>
 *
 * <p>Changing this to {@code NON_NULL} would reduce payload size but would be a
 * breaking change for existing API consumers.  If payload size is a concern,
 * migrate field-by-field with {@code @JsonInclude(NON_NULL)} annotations instead
 * of changing the global default.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // @formatter:off
        // Intentionally ALWAYS — see class-level javadoc for rationale.
        // @formatter:on
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return mapper;
    }
}
