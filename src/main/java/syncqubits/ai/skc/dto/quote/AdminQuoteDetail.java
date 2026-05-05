package syncqubits.ai.skc.dto.quote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuoteDetail {
    private UUID id;
    private String eventType;
    private LocalDate eventDate;
    private Integer guests;
    private String venue;
    private String budget;
    private String message;
    private String status;
    private Instant respondedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Client client;
    private List<Map<String, Object>> activity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Client {
        private UUID id;
        private String name;
        private String email;
        private String phone;
        private String status;
        private String source;
    }
}
