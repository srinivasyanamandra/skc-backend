package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import syncqubits.ai.skc.entity.embedded.AddressDoc;
import syncqubits.ai.skc.entity.embedded.NoteDoc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CRM root: identity, pipeline status, lifecycle, plus three embedded
 * collections that used to be separate child tables —
 *
 *   tags       TEXT[]   — replaces {@code client_tags} + {@code tags} registry
 *   addresses  JSONB    — replaces {@code client_addresses}
 *   notesLog   JSONB    — replaces {@code client_notes}
 *
 * The "single primary address" invariant is enforced by
 * {@link syncqubits.ai.skc.service.AdminClientService} (no DB-level
 * partial-unique index on JSONB; trade-off accepted for the simpler shape).
 */
@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_clients_email",     columnList = "email"),
    @Index(name = "idx_clients_status",    columnList = "status"),
    @Index(name = "idx_clients_lifecycle", columnList = "lifecycle_stage")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String source = "quote_request";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private ClientStatus status = ClientStatus.LEAD;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage", nullable = false, length = 40)
    @Builder.Default
    private LifecycleStage lifecycleStage = LifecycleStage.PROSPECT;

    @Column(name = "referral_source", length = 80)
    private String referralSource;

    @Column(name = "lifetime_value_cents", nullable = false)
    @Builder.Default
    private Long lifetimeValueCents = 0L;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "preferred_contact", length = 20)
    private String preferredContact;

    @Column(name = "dietary_notes", columnDefinition = "TEXT")
    private String dietaryNotes;

    /** Postgres TEXT[]. Stored case-insensitively on add — services
     *  normalize to lowercase before persisting so duplicates collapse. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<AddressDoc> addresses = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes_log", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<NoteDoc> notesLog = new ArrayList<>();

    /**
     * Defense-in-depth against legacy rows that pre-date the column add and
     * still have NULL collections. The DB-level migration already
     * backfills + enforces NOT NULL, so this is the belt to that
     * suspenders — if any older snapshot somehow returns NULL, the entity
     * stays operable in memory.
     */
    @PostLoad
    private void coalesceCollections() {
        if (tags      == null) tags      = new LinkedHashSet<>();
        if (addresses == null) addresses = new ArrayList<>();
        if (notesLog  == null) notesLog  = new ArrayList<>();
    }

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum ClientStatus {
        LEAD, CONTACTED, QUOTED, BOOKED, COMPLETED, CANCELLED
    }

    public enum LifecycleStage {
        PROSPECT, ACTIVE, REPEAT, VIP, DORMANT, ARCHIVED
    }
}
