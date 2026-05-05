package syncqubits.ai.skc.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInvitationSummary {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private String clientEmail;
    private String eventType;
    private LocalDate eventDate;
    private String token;
    private String reviewLink;
    private Instant sentAt;
    private Instant expiresAt;
    private Instant usedAt;
    private boolean expired;
    private boolean used;
    private Instant createdAt;
}
