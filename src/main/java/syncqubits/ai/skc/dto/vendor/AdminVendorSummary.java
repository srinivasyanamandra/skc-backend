package syncqubits.ai.skc.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVendorSummary {
    private UUID id;
    private String name;
    private String category;
    private String status;

    private String primaryContactName;
    private String primaryContactPhone;
    private String primaryContactEmail;

    private String city;
    private String state;

    private String paymentTerms;
    private String currency;

    private BigDecimal rating;
    private Integer ratingCount;

    private Long totalSpendCents;
    private Long outstandingCents;
    private long poCount;

    private Instant lastOrderedAt;
    private Set<String> tags;

    private Instant createdAt;
    private Instant updatedAt;
}
