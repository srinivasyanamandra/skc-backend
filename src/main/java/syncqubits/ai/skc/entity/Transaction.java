package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Single ledger of every cash movement.
 *
 * Replaces what would have been two tables (payments + expenses) with one
 * row whose {@link #direction} discriminates:
 *
 *   INCOMING — client paid us. Uses {@code clientId}, optional
 *              {@code invoiceId}, optional {@code bookingId}.
 *
 *   OUTGOING — we paid someone. Uses {@code category}, optional
 *              {@code vendorId}, optional {@code purchaseOrderId},
 *              optional {@code bookingId} (when the spend is
 *              event-attributable).
 *
 * Cash-flow and P&amp;L queries become trivial — {@code SELECT direction,
 * SUM(amount_cents) FROM transactions WHERE …} instead of UNIONing
 * separate ledgers. Refunds are tracked on the same row
 * (status=REFUNDED) rather than a sibling table; from the books'
 * perspective a refund is just the negation of the original event.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_direction", columnList = "direction"),
    @Index(name = "idx_tx_paid_at",   columnList = "paid_at"),
    @Index(name = "idx_tx_booking",   columnList = "booking_id"),
    @Index(name = "idx_tx_invoice",   columnList = "invoice_id"),
    @Index(name = "idx_tx_vendor",    columnList = "vendor_id"),
    @Index(name = "idx_tx_po",        columnList = "purchase_order_id"),
    @Index(name = "idx_tx_client",    columnList = "client_id"),
    @Index(name = "idx_tx_category",  columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable reference like {@code TXN-2026-000123}. */
    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Method method = Method.CASH;

    @Column(name = "paid_at", nullable = false)
    @Builder.Default
    private Instant paidAt = Instant.now();

    @Column(name = "transaction_ref", length = 120)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TxnStatus status = TxnStatus.RECORDED;

    /** Required for OUTGOING; ignored for INCOMING. */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ExpenseCategory category;

    /* ── attribution (subset applies per direction) ──────────────────────── */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(length = 300)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    /* ── refund (INCOMING only) ──────────────────────────────────────────── */

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Direction {
        INCOMING, OUTGOING
    }

    public enum Method {
        CASH, UPI, BANK_TRANSFER, CHEQUE, CARD, OTHER
    }

    public enum TxnStatus {
        RECORDED, RECONCILED, REFUNDED
    }

    /** Curated outgoing-spend categories. Drives dashboard segmentation
     *  and reporting; expanding the set is a deliberate business choice,
     *  not free-text on the row. */
    public enum ExpenseCategory {
        VENDOR_PAYMENT,
        RAW_INGREDIENTS,
        STAFF_SALARY,
        STAFF_WAGES,
        TRANSPORT,
        FUEL,
        UTILITIES,
        RENT,
        MARKETING,
        EQUIPMENT,
        MAINTENANCE,
        OFFICE,
        TAXES,
        BANK_CHARGES,
        OTHER
    }
}
