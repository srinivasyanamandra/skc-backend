package syncqubits.ai.skc.dto.vendor;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Partial-update for a rate card. All fields optional so the UI can flip
 * just {@code active} without resubmitting the item name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorRateCardUpdateRequest {
    @Size(max = 200)  private String itemName;
    @Size(max = 40)   private String unit;
    @PositiveOrZero   private Long unitPriceCents;
    @Size(max = 3)    private String currency;
                      private LocalDate validFrom;
                      private LocalDate validTo;
    @Size(max = 4000) private String notes;
                      private Boolean active;
}
