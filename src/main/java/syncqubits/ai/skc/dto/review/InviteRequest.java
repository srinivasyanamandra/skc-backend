package syncqubits.ai.skc.dto.review;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for POST /api/admin/reviews/invite.
 * Either {@code clientId} OR all of {name,email,phone} must be present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteRequest {
    private UUID clientId;

    @Size(max = 120)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    @NotBlank(message = "eventType is required")
    @Size(max = 40)
    private String eventType;

    @NotNull(message = "eventDate is required")
    private LocalDate eventDate;

    @Min(1)
    @Max(365)
    private Integer expiresInDays;

    /** Optional template id to use; falls back to first active REVIEW_INVITATION template. */
    private UUID templateId;
}
