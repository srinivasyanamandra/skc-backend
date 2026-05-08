package syncqubits.ai.skc.dto.invoice;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemRequest {
    /** Optional — set when updating an existing line; absent for new lines. */
    private UUID id;

    @NotBlank @Size(max = 300) private String description;

    @NotNull @Positive
    private BigDecimal quantity;

    @Size(max = 40) private String unit;

    @NotNull @PositiveOrZero
    private Long unitPriceCents;

    private Integer position;

    @Size(max = 4000) private String notes;
}
