package syncqubits.ai.skc.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminClientDetail {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String source;
    private String status;
    private String notes;

    /* Phase 1 — CRM-richer view */
    private String companyName;
    private String lifecycleStage;
    private String referralSource;
    private Long lifetimeValueCents;
    private Instant lastContactedAt;
    private String preferredContact;
    private String dietaryNotes;

    private Instant createdAt;
    private Instant updatedAt;

    private List<QuoteRef>          quotes;
    private List<ReviewRef>         reviews;
    private List<BookingRef>        bookings;
    private List<ClientNoteResponse>    timelineNotes;
    private List<ClientAddressResponse> addresses;
    /** Plain string set; tags are now a TEXT[] column on clients. */
    private Set<String>             tags;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteRef {
        private UUID id;
        private String eventType;
        private LocalDate eventDate;
        private Integer guests;
        private String status;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRef {
        private UUID id;
        private String type;
        private Short overallRating;
        private String status;
        private Boolean isFeatured;
        private Instant submittedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingRef {
        private UUID id;
        private String reference;
        private String eventType;
        private LocalDate eventDate;
        private Integer guestCount;
        private String status;
        private Long totalAmountCents;
        private Long paidAmountCents;
        private String currency;
        private Instant createdAt;
    }
}
