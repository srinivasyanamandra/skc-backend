package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import syncqubits.ai.skc.entity.embedded.LineItemDoc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Formal billing artefact for a booking. A booking can have multiple
 * invoices over its life — typically a deposit invoice on confirmation
 * and a balance invoice post-event. Computed totals are kept in sync by
 * {@link syncqubits.ai.skc.service.InvoiceService}; the {@code paidCents}
 * column is rolled up from non-refunded INCOMING transactions tagged to
 * this invoice id.
 *
 * Lifecycle:
 *   DRAFT → ISSUED → PARTIALLY_PAID → PAID
 *   ISSUED → OVERDUE (auto-flagged once due_date passes with paid &lt; total)
 *   ISSUED / PARTIALLY_PAID → VOID (terminal; refunds happen via Transaction.status)
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoices_booking",  columnList = "booking_id"),
    @Index(name = "idx_invoices_client",   columnList = "client_id"),
    @Index(name = "idx_invoices_status",   columnList = "status"),
    @Index(name = "idx_invoices_due_date", columnList = "due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Denormalised — list views render the client name without joining
     *  through bookings. Always equal to {@code booking.client}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date") private LocalDate issueDate;
    @Column(name = "due_date")   private LocalDate dueDate;

    @Column(name = "sent_at")    private Instant sentAt;
    @Column(name = "voided_at")  private Instant voidedAt;

    @Column(name = "subtotal_cents", nullable = false)
    @Builder.Default
    private Long subtotalCents = 0L;

    @Column(name = "tax_cents", nullable = false)
    @Builder.Default
    private Long taxCents = 0L;

    @Column(name = "discount_cents", nullable = false)
    @Builder.Default
    private Long discountCents = 0L;

    @Column(name = "total_cents", nullable = false)
    @Builder.Default
    private Long totalCents = 0L;

    @Column(name = "paid_cents", nullable = false)
    @Builder.Default
    private Long paidCents = 0L;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(columnDefinition = "TEXT") private String terms;
    @Column(columnDefinition = "TEXT") private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<LineItemDoc> items = new ArrayList<>();

    /** Defense-in-depth against legacy rows with NULL items. */
    @PostLoad
    private void coalesceCollections() {
        if (items == null) items = new ArrayList<>();
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum InvoiceStatus {
        DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, VOID
    }
}
