package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Element of {@code vendors.contacts} JSONB array — additional people /
 *  desks at a vendor beyond the denormalised primary contact on the row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactDoc {
    private UUID id;
    private String name;
    private String role;
    private String phone;
    private String email;
    private Boolean primary;
    private String notes;
    private Instant createdAt;
}
