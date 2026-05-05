package syncqubits.ai.skc.dto.quote;

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
public class AdminQuoteSummary {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private String eventType;
    private LocalDate eventDate;
    private Integer guests;
    private String venue;
    private String budget;
    private String status;
    private Instant respondedAt;
    private Instant createdAt;
}
