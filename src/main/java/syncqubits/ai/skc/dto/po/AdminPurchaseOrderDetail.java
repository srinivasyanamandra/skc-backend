package syncqubits.ai.skc.dto.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPurchaseOrderDetail {
    private UUID id;
    private String reference;
    private String status;

    private VendorRef vendor;
    private BookingRef booking;

    private LocalDate issueDate;
    private LocalDate expectedDelivery;
    private Instant receivedAt;

    private Long subtotalCents;
    private Long taxCents;
    private Long totalCents;
    private Long paidCents;
    private String currency;

    private String internalNotes;
    private String vendorNotes;

    private List<Item> items;

    private Instant createdAt;
    private Instant updatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VendorRef {
        private UUID id;
        private String name;
        private String category;
        private String paymentTerms;
        private String primaryContactName;
        private String primaryContactPhone;
        private String primaryContactEmail;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingRef {
        private UUID id;
        private String reference;
        private String eventType;
        private LocalDate eventDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private UUID id;
        private String description;
        private BigDecimal quantity;
        private String unit;
        private Long unitPriceCents;
        private Long lineTotalCents;
        private Integer position;
        private UUID rateCardId;
        private String notes;
    }
}
