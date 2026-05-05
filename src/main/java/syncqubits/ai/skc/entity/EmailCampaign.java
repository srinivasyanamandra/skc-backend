package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "email_campaigns", indexes = {
    @Index(name = "idx_campaigns_status", columnList = "status"),
    @Index(name = "idx_campaigns_schedule", columnList = "scheduled_at"),
    @Index(name = "idx_campaigns_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> recipients = List.of();

    @Column(nullable = false)
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> config = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> globalVariables = Map.of();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_template_id")
    private EmailTemplate defaultTemplate;

    private Instant scheduledAt;

    private Instant startedAt;

    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum CampaignStatus {
        DRAFT, QUEUED, SENDING, SENT, FAILED, CANCELLED
    }
}
