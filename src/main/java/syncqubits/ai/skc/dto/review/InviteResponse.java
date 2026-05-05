package syncqubits.ai.skc.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {
    private UUID id;
    private UUID clientId;
    private String reviewLink;
    private String token;
    private Instant expiresAt;
    private Instant sentAt;
    /** True if SMTP attempted (with or without success). */
    private boolean emailQueued;
    /** True iff SMTP returned success on at least one attempt. */
    private boolean emailDelivered;
    /** SMTP message-id from the server, if available. */
    private String messageId;
    /** Failure reason, if {@code emailDelivered == false}. */
    private String error;
    /** Name of the template that was used. */
    private String templateUsed;
}
