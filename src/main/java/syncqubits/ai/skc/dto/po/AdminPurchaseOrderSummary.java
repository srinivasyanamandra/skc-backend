package syncqubits.ai.skc.dto.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPurchaseOrderSummary {
    private UUID id;
    private String reference;
    private String status;

    private UUID vendorId;
    private String vendorName;
    private String vendorCategory;

    private UUID bookingId;
    private String bookingReference;

    private LocalDate issueDate;
    private LocalDate expectedDelivery;

    private Long totalCents;
    private Long paidCents;
    private Long outstandingCents;
    private String currency;

    private int itemCount;

    private Instant createdAt;
}
