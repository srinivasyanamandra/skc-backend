package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_reviews_token", columnList = "token"),
    @Index(name = "idx_reviews_expires", columnList = "expires_at"),
    @Index(name = "idx_reviews_public", columnList = "is_public, created_at"),
    @Index(name = "idx_reviews_featured", columnList = "is_featured"),
    @Index(name = "idx_reviews_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewType type;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private LocalDate eventDate;

    // Invitation columns
    @Column(unique = true, length = 64)
    private String token;

    private Instant expiresAt;

    private Instant sentAt;

    private Instant usedAt;

    // Submitted review columns
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private Review invitation;

    @Column(length = 120)
    private String reviewerName;

    private Short overallRating;

    private Short foodQualityRating;

    private Short tasteRating;

    private Short presentationRating;

    private Short staffBehaviorRating;

    private Short timelinessRating;

    private Short serviceQualityRating;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @Column(length = 10)
    private String recommend;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Builder.Default
    private Boolean isFeatured = false;

    @Builder.Default
    private Boolean isPublic = false;

    private Instant moderatedAt;

    private Instant submittedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum ReviewType {
        INVITATION, REVIEW
    }

    public enum ReviewStatus {
        PENDING, APPROVED, REJECTED
    }
}
