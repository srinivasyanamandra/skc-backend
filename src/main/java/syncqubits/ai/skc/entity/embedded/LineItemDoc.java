package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared line-item shape for {@code purchase_orders.items} and
 * {@code invoices.items}. Quantity is BigDecimal to keep fractional units
 * exact (12.5 kg of paneer); price + line total stay in integer cents.
 *
 * {@code rateCardId} is only set on PO lines that locked in a vendor's
 * catalog price. Invoice lines leave it null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LineItemDoc {
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
