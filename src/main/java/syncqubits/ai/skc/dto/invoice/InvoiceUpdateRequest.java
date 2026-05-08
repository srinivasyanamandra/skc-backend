package syncqubits.ai.skc.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Partial-update for an invoice. Items, when supplied, replace the line
 * set entirely (the service diffs by id — update / insert / delete).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceUpdateRequest {

    @Size(max = 20) private String status;

    private LocalDate issueDate;
    private LocalDate dueDate;

    @PositiveOrZero private Long taxCents;
    @PositiveOrZero private Long discountCents;

    @Size(max = 3)    private String currency;
    @Size(max = 4000) private String terms;
    @Size(max = 4000) private String notes;

    @Valid
    private List<InvoiceItemRequest> items;
}
