package syncqubits.ai.skc.dto.campaign;

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
public class CampaignSummary {
    private UUID id;
    private String name;
    private String status;
    private int totalRecipients;
    private int sentCount;
    private int failedCount;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
