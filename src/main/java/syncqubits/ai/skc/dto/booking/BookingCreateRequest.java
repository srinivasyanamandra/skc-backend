package syncqubits.ai.skc.dto.booking;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequest {

    @NotNull
    private UUID clientId;

    /** Optional — set to link this booking to the quote it converts. */
    private UUID quoteRequestId;

    @NotBlank @Size(max = 40)
    private String eventType;

    @NotNull
    private LocalDate eventDate;

    private LocalTime eventStartTime;
    private LocalTime eventEndTime;

    @NotNull @Min(1)
    private Integer guestCount;

    @Size(max = 200)
    private String venueName;

    @Size(max = 4000)
    private String venueAddress;

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

    @Size(max = 4000)
    private String clientNotes;
}
