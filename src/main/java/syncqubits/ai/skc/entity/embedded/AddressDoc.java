package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Element of {@code clients.addresses} JSONB array. Lombok {@code @Data}
 * keeps it mutable so services can update fields in place; the parent
 * {@code Client} entity flushes the whole list back to JSONB on save.
 *
 * The class is plain (not a record) so Jackson + Hibernate's JSON type
 * round-trip works without a constructor argument list to maintain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDoc {
    private UUID id;
    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    /** Field name avoids Java's {@code primary} keyword collisions
     *  via lombok-generated isPrimary()/setPrimary(). */
    private Boolean primary;
    private Instant createdAt;
}
