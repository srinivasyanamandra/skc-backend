package syncqubits.ai.skc.entity.embedded;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/** Element of {@code vendors.rate_cards} JSONB array — a known unit price
 *  used to autocomplete PO line items. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateCardDoc {
    private UUID id;
    private String itemName;
    private String unit;
    private Long unitPriceCents;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean active;
    private String notes;
}
