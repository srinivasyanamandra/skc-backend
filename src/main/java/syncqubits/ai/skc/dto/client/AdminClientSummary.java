package syncqubits.ai.skc.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminClientSummary {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String source;
    private String status;
    private String lifecycleStage;
    private String companyName;
    private long quoteCount;
    private long reviewCount;
    private long bookingCount;
    private Long lifetimeValueCents;
    private Instant lastContactedAt;
    private Set<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
}
