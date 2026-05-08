package syncqubits.ai.skc.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingSummary {
    private UUID id;
    private String reference;

    private UUID clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;

    private String eventType;
    private LocalDate eventDate;
    private Integer guestCount;
    private String venueName;
    private String packageName;

    private String status;

    private Long totalAmountCents;
    private Long paidAmountCents;
    private String currency;

    private long openTaskCount;

    private Instant createdAt;
}
