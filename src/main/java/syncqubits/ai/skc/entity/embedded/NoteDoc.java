package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Element of {@code clients.notes_log} JSONB array — a single timestamped
 *  CRM note with author and pinning. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoteDoc {
    private UUID id;
    private String body;
    private String category;
    private Boolean pinned;
    private String authorEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
