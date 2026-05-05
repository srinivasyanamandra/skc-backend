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
public class AdminReviewSummary {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private String clientEmail;
    private String reviewerName;
    private String eventType;
    private LocalDate eventDate;
    private Short overallRating;
    private String comments;
    private String suggestions;
    private String recommend;
    private String status;
    private Boolean isFeatured;
    private Boolean isPublic;
    private Instant moderatedAt;
    private Instant submittedAt;
    private Instant createdAt;
}
