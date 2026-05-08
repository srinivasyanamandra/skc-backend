package syncqubits.ai.skc.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreateRequest {

    @NotNull
    private UUID bookingId;

    @Size(max = 20)
    private String status;

    private LocalDate issueDate;
    private LocalDate dueDate;

    @PositiveOrZero private Long taxCents;
    @PositiveOrZero private Long discountCents;

    @Size(max = 3) private String currency;

    @Size(max = 4000) private String terms;
    @Size(max = 4000) private String notes;

    @Valid
    private List<InvoiceItemRequest> items;

    /** Convenience flag — when true, the service auto-fills items with the
     *  booking's package + balance (total - sum of existing invoices).
     *  Useful for the one-click "Generate invoice" action on BookingDetail. */
    private Boolean seedFromBooking;
}
