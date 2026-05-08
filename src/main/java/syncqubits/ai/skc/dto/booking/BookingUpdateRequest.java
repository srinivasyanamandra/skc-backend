package syncqubits.ai.skc.dto.booking;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** Partial-update request — every field is optional; only non-null fields are
 *  applied. Status changes go through this same endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingUpdateRequest {

    @Size(max = 40)  private String eventType;
                     private LocalDate eventDate;
                     private LocalTime eventStartTime;
                     private LocalTime eventEndTime;

    @Min(1)          private Integer guestCount;

    @Size(max = 200) private String venueName;
    @Size(max = 4000) private String venueAddress;

    @Size(max = 40)  private String serviceStyle;
    @Size(max = 160) private String packageName;

    @Size(max = 40)  private String status;

    @PositiveOrZero  private Long totalAmountCents;
    @PositiveOrZero  private Long depositAmountCents;
    @PositiveOrZero  private Long paidAmountCents;
    @Size(max = 3)   private String currency;

    @Size(max = 4000) private String internalNotes;
    @Size(max = 4000) private String clientNotes;
}
