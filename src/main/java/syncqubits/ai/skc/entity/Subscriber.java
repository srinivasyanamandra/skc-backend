package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscribers", indexes = {
    @Index(name = "idx_subscribers_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String source = "website";

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private Instant unsubscribedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
