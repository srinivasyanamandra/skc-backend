package syncqubits.ai.skc.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingDetail {
    private UUID id;
    private String reference;

    private Client client;
    private UUID quoteRequestId;

    private String eventType;
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;
    private Integer guestCount;

    private String venueName;
    private String venueAddress;

    private String serviceStyle;
    private String packageName;

    private String status;

    private Long totalAmountCents;
    private Long depositAmountCents;
    private Long paidAmountCents;
    private Long invoicedAmountCents;
    private Long directExpenseCents;
    private String currency;

    /** Snapshot finance summary so the booking screen renders P&L without
     *  a second round-trip. {@code grossMarginCents} = paid - (PO paid +
     *  direct expenses). */
    private FinanceSummary finance;

    private String internalNotes;
    private String clientNotes;

    private Map<String, Object> menuSummary;
    private Map<String, Object> staffing;

    private List<Task> tasks;
    private List<PoRef> purchaseOrders;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Client {
        private UUID id;
        private String name;
        private String email;
        private String phone;
        private String status;
        private String lifecycleStage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {
        private UUID id;
        private String title;
        private String description;
        private Instant dueAt;
        private String assignee;
        private String status;
        private Integer position;
        private Instant completedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoRef {
        private UUID id;
        private String reference;
        private String status;
        private UUID vendorId;
        private String vendorName;
        private String vendorCategory;
        private LocalDate issueDate;
        private LocalDate expectedDelivery;
        private Long totalCents;
        private Long paidCents;
        private String currency;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceSummary {
        private Long bookingTotalCents;
        private Long invoicedCents;
        private Long invoiceOutstandingCents;
        private Long paidCents;
        private Long advanceUnallocatedCents;     // payments not yet tagged to an invoice
        private Long vendorCommittedCents;        // sum of PO totals (non-cancelled)
        private Long vendorPaidCents;             // sum of PO paid_cents
        private Long directExpenseCents;          // booking-attributed OUTGOING (no PO)
        private Long totalCostCents;              // vendorPaidCents + directExpenseCents
        private Long grossMarginCents;            // paidCents - totalCostCents
        private List<InvoiceRef> invoices;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceRef {
        private UUID id;
        private String reference;
        private String status;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private Long totalCents;
        private Long paidCents;
        private String currency;
        private Instant createdAt;
    }
}
