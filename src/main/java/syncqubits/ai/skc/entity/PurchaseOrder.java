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
 * Purchase order against a vendor. Line items used to live in a separate
 * table; they're now a JSONB array on this row because they're always
 * loaded with the PO and never queried independently. The {@link
 * syncqubits.ai.skc.service.PurchaseOrderService} recomputes
 * subtotal/total/paid on every mutation so the list view never has to
 * fan out into items.
 */
@Entity
@Table(name = "purchase_orders", indexes = {
    @Index(name = "idx_pos_vendor",  columnList = "vendor_id"),
    @Index(name = "idx_pos_booking", columnList = "booking_id"),
    @Index(name = "idx_pos_status",  columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PoStatus status = PoStatus.DRAFT;

    @Column(name = "issue_date")        private LocalDate issueDate;
    @Column(name = "expected_delivery") private LocalDate expectedDelivery;
    @Column(name = "received_at")       private Instant   receivedAt;

    @Column(name = "subtotal_cents", nullable = false)
    @Builder.Default
    private Long subtotalCents = 0L;

    @Column(name = "tax_cents", nullable = false)
    @Builder.Default
    private Long taxCents = 0L;

    @Column(name = "total_cents", nullable = false)
    @Builder.Default
    private Long totalCents = 0L;

    @Column(name = "paid_cents", nullable = false)
    @Builder.Default
    private Long paidCents = 0L;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "vendor_notes", columnDefinition = "TEXT")
    private String vendorNotes;

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

    public enum PoStatus {
        DRAFT, ISSUED, CONFIRMED, PARTIAL, RECEIVED, PAID, CANCELLED
    }
}
