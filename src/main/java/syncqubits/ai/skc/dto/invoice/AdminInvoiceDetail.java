package syncqubits.ai.skc.dto.invoice;

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
public class AdminInvoiceDetail {
    private UUID id;
    private String reference;
    private String status;

    private BookingRef booking;
    private ClientRef client;

    private LocalDate issueDate;
    private LocalDate dueDate;
    private Instant   sentAt;
    private Instant   voidedAt;

    private Long subtotalCents;
    private Long taxCents;
    private Long discountCents;
    private Long totalCents;
    private Long paidCents;
    private String currency;

    private String terms;
    private String notes;

    private List<Item> items;
    private List<PaymentRef> payments;

    private Instant createdAt;
    private Instant updatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingRef {
        private UUID id;
        private String reference;
        private String eventType;
        private LocalDate eventDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ClientRef {
        private UUID id;
        private String name;
        private String email;
        private String phone;
        private String companyName;
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
        private String notes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentRef {
        private UUID id;
        private String reference;
        private Long amountCents;
        private String currency;
        private String method;
        private String status;
        private Instant paidAt;
        private String transactionRef;
    }
}
