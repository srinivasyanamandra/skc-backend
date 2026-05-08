package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import syncqubits.ai.skc.entity.embedded.ContactDoc;
import syncqubits.ai.skc.entity.embedded.RateCardDoc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Supplier directory entry. Contacts and rate cards used to be 1:N child
 * tables; they're now JSONB arrays on this row because they're always
 * loaded with the vendor and never queried independently.
 */
@Entity
@Table(name = "vendors", indexes = {
    @Index(name = "idx_vendors_category", columnList = "category"),
    @Index(name = "idx_vendors_status",   columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VendorStatus status = VendorStatus.ACTIVE;

    @Column(name = "primary_contact_name",  length = 120) private String primaryContactName;
    @Column(name = "primary_contact_phone", length = 20)  private String primaryContactPhone;
    @Column(name = "primary_contact_email", length = 255) private String primaryContactEmail;

    @Column(name = "gst_number", length = 32) private String gstNumber;
    @Column(name = "pan_number", length = 20) private String panNumber;
    @Column(length = 255)                     private String website;

    @Column(name = "address_line1", length = 200) private String addressLine1;
    @Column(name = "address_line2", length = 200) private String addressLine2;
    @Column(length = 80)                          private String city;
    @Column(length = 80)                          private String state;
    @Column(length = 20)                          private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", nullable = false, length = 40)
    @Builder.Default
    private PaymentTerms paymentTerms = PaymentTerms.NET_30;

    @Column(name = "preferred_payment", length = 40)
    private String preferredPayment;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /* ── aggregates (maintained by services) ────────────────────────────── */

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "total_spend_cents", nullable = false)
    @Builder.Default
    private Long totalSpendCents = 0L;

    @Column(name = "last_ordered_at")
    private Instant lastOrderedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /* ── embedded collections ───────────────────────────────────────────── */

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<ContactDoc> contacts = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rate_cards", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<RateCardDoc> rateCards = new ArrayList<>();

    /** See {@link Client#coalesceCollections()} — same defense for legacy rows. */
    @PostLoad
    private void coalesceCollections() {
        if (tags       == null) tags       = new LinkedHashSet<>();
        if (contacts   == null) contacts   = new ArrayList<>();
        if (rateCards  == null) rateCards  = new ArrayList<>();
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum VendorCategory {
        DECOR, FLORIST, LIGHTING, EQUIPMENT, TRANSPORT,
        STAFFING, RAW_INGREDIENTS, BEVERAGES, ENTERTAINMENT,
        VENUE, PHOTOGRAPHY, MISCELLANEOUS
    }

    public enum VendorStatus {
        ACTIVE, INACTIVE, BLOCKED
    }

    public enum PaymentTerms {
        ADVANCE, ON_DELIVERY, NET_7, NET_15, NET_30, NET_45, NET_60
    }
}
