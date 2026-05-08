package syncqubits.ai.skc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a Jackson {@link ObjectMapper} with JSR-310 (date/time) support
 * into Hibernate as the format mapper for {@code jsonb} columns.
 *
 * Why this exists:
 *   • Spring Boot 4 ships Jackson 3 (the {@code tools.jackson.*} family)
 *     as the primary HTTP serializer.
 *   • Hibernate 7's only built-in {@code JsonFormatMapper} is
 *     {@link JacksonJsonFormatMapper}, which is compiled against the
 *     legacy Jackson 2 ({@code com.fasterxml.jackson.*}) namespace.
 *   • By default, that mapper constructs a bare Jackson 2 {@code ObjectMapper}
 *     with no modules registered, so {@code java.time.Instant} fields
 *     inside JSONB document records (e.g. {@code NoteDoc.createdAt},
 *     {@code TaskDoc.dueAt}) fail with "Java 8 date/time type
 *     {@code java.time.Instant} not supported by default" on any insert.
 *
 * The fix is to build our own Jackson 2 mapper with {@link JavaTimeModule}
 * registered and hand it to Hibernate via
 * {@link HibernatePropertiesCustomizer}. We construct this mapper locally
 * rather than reusing Spring's primary {@code ObjectMapper} bean because
 * the Spring-side bean is now Jackson 3 — a different type tree entirely.
 */
@Configuration
public class JpaJsonConfig {

    @Bean
    public HibernatePropertiesCustomizer jacksonHibernateJsonFormatMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Match the public API conventions: ISO-8601 strings, not
                // millis-since-epoch numbers — easier to read in psql and
                // backwards-compatible with anything else that might
                // touch the JSON.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return props -> props.put(
                AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(mapper));
    }
}
