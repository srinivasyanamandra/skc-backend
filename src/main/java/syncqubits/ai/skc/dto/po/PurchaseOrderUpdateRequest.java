package syncqubits.ai.skc.dto.po;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partial-update for a PO. Every field is optional; only non-null fields
 * are applied. Items, when provided, replace the existing line set entirely
 * (lines with an {@code id} are updated in place; lines without are
 * inserted; existing lines whose ids are not in the request are deleted).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderUpdateRequest {

    private UUID bookingId;

    @Size(max = 20) private String status;

    private LocalDate issueDate;
    private LocalDate expectedDelivery;

    @PositiveOrZero private Long taxCents;
    @PositiveOrZero private Long paidCents;

    @Size(max = 3) private String currency;

    @Size(max = 4000) private String internalNotes;
    @Size(max = 4000) private String vendorNotes;

    @Valid
    private List<PurchaseOrderItemRequest> items;
}
