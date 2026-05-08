package syncqubits.ai.skc.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTaskRequest {

    @NotBlank @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String description;

    private Instant dueAt;

    @Size(max = 120)
    private String assignee;

    /** Optional — defaults to TODO on create; required to be a valid value
     *  on update (TODO/IN_PROGRESS/DONE/BLOCKED). */
    @Size(max = 20)
    private String status;

    private Integer position;
}
