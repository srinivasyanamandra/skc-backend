package syncqubits.ai.skc.dto.transaction;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Partial-update; for refunds use the dedicated refund endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionUpdateRequest {

    @PositiveOrZero  private Long amountCents;
    @Size(max = 3)   private String currency;
    @Size(max = 20)  private String method;
    private Instant  paidAt;
    @Size(max = 120) private String transactionRef;

    @Size(max = 40)  private String category;
    @Size(max = 20)  private String status;     // RECORDED / RECONCILED

    @Size(max = 300)  private String description;
    @Size(max = 4000) private String notes;
    @Size(max = 500)  private String receiptUrl;
}
