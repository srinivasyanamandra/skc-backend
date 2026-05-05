package syncqubits.ai.skc.dto.subscriber;

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
public class AdminSubscriberSummary {
    private UUID id;
    private String email;
    private String name;
    private String source;
    private Boolean isActive;
    private Instant unsubscribedAt;
    private Instant createdAt;
}
