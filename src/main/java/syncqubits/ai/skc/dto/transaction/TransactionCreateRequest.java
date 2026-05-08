package syncqubits.ai.skc.dto.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCreateRequest {

    /** "incoming" or "outgoing". */
    @NotBlank @Size(max = 10)
    private String direction;

    @NotNull @PositiveOrZero
    private Long amountCents;

    @Size(max = 3) private String currency;

    @Size(max = 20) private String method;          // CASH/UPI/BANK_TRANSFER/...
    private Instant paidAt;
    @Size(max = 120) private String transactionRef;

    /** Required when direction=outgoing. */
    @Size(max = 40) private String category;

    /* attribution — different fields apply per direction */
    private UUID clientId;
    private UUID invoiceId;
    private UUID vendorId;
    private UUID purchaseOrderId;
    private UUID bookingId;

    @Size(max = 300)  private String description;
    @Size(max = 4000) private String notes;
    @Size(max = 500)  private String receiptUrl;
}
