package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Element of {@code bookings.tasks} JSONB array — one event-day checklist
 *  item. {@code status} stays as a lowercase string ("todo" / "in_progress"
 *  / "done" / "blocked") for direct UI consumption without enum mapping. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDoc {
    private UUID id;
    private String title;
    private String description;
    private Instant dueAt;
    private String assignee;
    private String status;
    private Integer position;
    private Instant completedAt;
    private Instant createdAt;
}
