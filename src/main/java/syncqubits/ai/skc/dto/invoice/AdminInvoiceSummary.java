package syncqubits.ai.skc.dto.invoice;

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
public class AdminInvoiceSummary {
    private UUID id;
    private String reference;
    private String status;

    private UUID bookingId;
    private String bookingReference;

    private UUID clientId;
    private String clientName;
    private String clientEmail;

    private LocalDate issueDate;
    private LocalDate dueDate;

    private Long totalCents;
    private Long paidCents;
    private Long outstandingCents;
    private String currency;

    private int itemCount;

    private Instant createdAt;
}
