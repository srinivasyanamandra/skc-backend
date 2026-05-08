package syncqubits.ai.skc.dto.po;

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
public class PurchaseOrderCreateRequest {

    @NotNull
    private UUID vendorId;

    /** Optional — null for ad-hoc / inventory POs. */
    private UUID bookingId;

    @Size(max = 20)
    private String status;

    private LocalDate issueDate;
    private LocalDate expectedDelivery;

    @PositiveOrZero
    private Long taxCents;

    @Size(max = 3)
    private String currency;

    @Size(max = 4000) private String internalNotes;
    @Size(max = 4000) private String vendorNotes;

    @Valid
    private List<PurchaseOrderItemRequest> items;
}
