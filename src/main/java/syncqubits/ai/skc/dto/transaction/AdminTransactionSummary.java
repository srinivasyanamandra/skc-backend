package syncqubits.ai.skc.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Row in the unified cash-flow ledger. {@code direction} discriminates
 * incoming (payment from a client) vs outgoing (expense / vendor payment);
 * the attribution fields are filled in only for the relevant direction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTransactionSummary {
    private UUID id;
    private String reference;
    private String direction;       // "incoming" | "outgoing"
    private String status;
    private String method;
    private String category;        // outgoing only

    private Long amountCents;
    private String currency;

    private Instant paidAt;
    private String transactionRef;

    private String description;

    /* attribution */
    private UUID clientId;
    private String clientName;
    private UUID invoiceId;
    private String invoiceReference;
    private UUID vendorId;
    private String vendorName;
    private UUID purchaseOrderId;
    private String purchaseOrderReference;
    private UUID bookingId;
    private String bookingReference;

    private Instant createdAt;
}
