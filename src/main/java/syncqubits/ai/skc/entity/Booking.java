package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import syncqubits.ai.skc.entity.embedded.TaskDoc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Confirmed event / catering job. Owns the lifecycle from confirmation
 * through delivery; everything downstream (invoices, transactions,
 * purchase orders, event-day checklist) attaches here.
 *
 * Aggregate columns ({@code paidAmountCents}, {@code invoicedAmountCents},
 * {@code directExpenseCents}) are maintained by their owning services so
 * the booking finance card never has to fan out into transactions / POs
 * on every render. {@code tasks} replaces the former {@code booking_tasks}
 * table — same operations, simpler shape.
 */
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_client",     columnList = "client_id"),
    @Index(name = "idx_bookings_quote",      columnList = "quote_request_id"),
    @Index(name = "idx_bookings_status",     columnList = "status"),
    @Index(name = "idx_bookings_event_date", columnList = "event_date"),
    @Index(name = "idx_bookings_created",    columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_request_id")
    private QuoteRequest quoteRequest;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_start_time")
    private LocalTime eventStartTime;

    @Column(name = "event_end_time")
    private LocalTime eventEndTime;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "venue_address", columnDefinition = "TEXT")
    private String venueAddress;

    @Column(name = "service_style", length = 40)
    private String serviceStyle;

    @Column(name = "package_name", length = 160)
    private String packageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    /* ── money roll-ups ─────────────────────────────────────────────────── */

    @Column(name = "total_amount_cents", nullable = false)
    @Builder.Default
    private Long totalAmountCents = 0L;

    @Column(name = "deposit_amount_cents", nullable = false)
    @Builder.Default
    private Long depositAmountCents = 0L;

    /** Sum of non-refunded INCOMING transactions tagged to this booking.
     *  Maintained by {@code TransactionService}. */
    @Column(name = "paid_amount_cents", nullable = false)
    @Builder.Default
    private Long paidAmountCents = 0L;

    /** Sum of non-VOID invoice totals on this booking. Maintained by
     *  {@code InvoiceService}. */
    @Column(name = "invoiced_amount_cents", nullable = false)
    @Builder.Default
    private Long invoicedAmountCents = 0L;

    /** Sum of OUTGOING transactions tagged to this booking but NOT linked
     *  to a purchase order (PO settlements are tracked on the PO itself).
     *  Maintained by {@code TransactionService}. */
    @Column(name = "direct_expense_cents", nullable = false)
    @Builder.Default
    private Long directExpenseCents = 0L;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /* ── ops ────────────────────────────────────────────────────────────── */

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "client_notes", columnDefinition = "TEXT")
    private String clientNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "menu_summary", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> menuSummary = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> staffing = new HashMap<>();

    /** Replaces the former {@code booking_tasks} table. Per-booking
     *  checklist; never queried across bookings (use the dashboard's
     *  upcoming-events feed instead). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<TaskDoc> tasks = new ArrayList<>();

    /** See {@link Client#coalesceCollections()} — defends against legacy
     *  rows whose JSONB columns were created nullable by an earlier
     *  Hibernate ddl-auto pass. */
    @PostLoad
    private void coalesceCollections() {
        if (menuSummary == null) menuSummary = new HashMap<>();
        if (staffing    == null) staffing    = new HashMap<>();
        if (tasks       == null) tasks       = new ArrayList<>();
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum BookingStatus {
        CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, POSTPONED
    }
}
