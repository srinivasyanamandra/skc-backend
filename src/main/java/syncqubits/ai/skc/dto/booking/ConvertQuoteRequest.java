package syncqubits.ai.skc.dto.booking;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Convert an existing QuoteRequest into a Booking. All fields default from
 * the source quote; the caller can override any of them at conversion time
 * (e.g. lock in a specific event time, set the package, or record the
 * agreed total).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertQuoteRequest {

    private LocalTime eventStartTime;
    private LocalTime eventEndTime;

    @Size(max = 40)
    private String serviceStyle;

    @Size(max = 160)
    private String packageName;

    @PositiveOrZero
    private Long totalAmountCents;

    @PositiveOrZero
    private Long depositAmountCents;

    @Size(max = 3)
    private String currency;

    @Size(max = 4000)
    private String internalNotes;

    /** Whether to seed a default checklist (venue confirmation, menu sign-off,
     *  staff briefing, etc.). Defaults to true. */
    private Boolean seedDefaultTasks;
}
