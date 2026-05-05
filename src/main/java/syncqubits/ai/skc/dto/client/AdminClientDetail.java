package syncqubits.ai.skc.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminClientDetail {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String source;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    private List<QuoteRef> quotes;
    private List<ReviewRef> reviews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteRef {
        private UUID id;
        private String eventType;
        private LocalDate eventDate;
        private Integer guests;
        private String status;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRef {
        private UUID id;
        private String type;
        private Short overallRating;
        private String status;
        private Boolean isFeatured;
        private Instant submittedAt;
        private Instant createdAt;
    }
}
