package syncqubits.ai.skc.dto.po;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line on a PO. Quantity is BigDecimal to support fractional units
 * (e.g. 12.5 kg). Unit price is in cents. The service computes
 * lineTotalCents = round(quantity * unitPriceCents) so callers don't need
 * to send a derived field that could disagree.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemRequest {

    /** Optional — set when updating an existing line; absent for new lines. */
    private UUID id;

    @NotBlank @Size(max = 300)
    private String description;

    @NotNull @Positive
    private BigDecimal quantity;

    @Size(max = 40)
    private String unit;

    @NotNull @PositiveOrZero
    private Long unitPriceCents;

    private Integer position;

    private UUID rateCardId;

    @Size(max = 4000)
    private String notes;
}
