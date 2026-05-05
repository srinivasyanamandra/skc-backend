package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_logs", indexes = {
    @Index(name = "idx_logs_type", columnList = "type"),
    @Index(name = "idx_logs_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_logs_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 40)
    private String entityType;

    private UUID entityId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(columnDefinition = "inet")
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
