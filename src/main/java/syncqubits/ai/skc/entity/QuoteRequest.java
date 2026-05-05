package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quote_requests", indexes = {
    @Index(name = "idx_quotes_client", columnList = "client_id"),
    @Index(name = "idx_quotes_status", columnList = "status"),
    @Index(name = "idx_quotes_date", columnList = "event_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private Integer guests;

    @Column(length = 200)
    private String venue;

    @Column(length = 40)
    private String budget;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.PENDING;

    private Instant respondedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum QuoteStatus {
        PENDING, CONTACTED, QUOTED, BOOKED, DECLINED
    }
}
