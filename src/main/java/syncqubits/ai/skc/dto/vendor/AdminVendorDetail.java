package syncqubits.ai.skc.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVendorDetail {
    private UUID id;
    private String name;
    private String category;
    private String status;

    /* primary contact */
    private String primaryContactName;
    private String primaryContactPhone;
    private String primaryContactEmail;

    /* compliance */
    private String gstNumber;
    private String panNumber;
    private String website;

    /* address */
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;

    /* commercial */
    private String paymentTerms;
    private String preferredPayment;
    private String currency;

    /* aggregates */
    private BigDecimal rating;
    private Integer ratingCount;
    private Long totalSpendCents;
    private Long outstandingCents;
    private Instant lastOrderedAt;

    private String notes;

    private Set<String> tags;
    private List<ContactRef> contacts;
    private List<RateCardRef> rateCards;
    private List<PoRef> recentPurchaseOrders;

    private Instant createdAt;
    private Instant updatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ContactRef {
        private UUID id;
        private String name;
        private String role;
        private String phone;
        private String email;
        private Boolean primary;
        private String notes;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RateCardRef {
        private UUID id;
        private String itemName;
        private String unit;
        private Long unitPriceCents;
        private String currency;
        private LocalDate validFrom;
        private LocalDate validTo;
        private Boolean active;
        private String notes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PoRef {
        private UUID id;
        private String reference;
        private String status;
        private LocalDate issueDate;
        private LocalDate expectedDelivery;
        private Long totalCents;
        private Long paidCents;
        private String currency;
        private UUID bookingId;
        private String bookingReference;
        private Instant createdAt;
    }
}
