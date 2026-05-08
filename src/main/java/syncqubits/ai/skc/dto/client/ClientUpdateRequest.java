package syncqubits.ai.skc.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientUpdateRequest {

    @Size(max = 120)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 40)
    private String status;

    @Size(max = 4000)
    private String notes;

    /* Phase 1 — extended CRM fields. All optional; only non-null fields are
     * applied so existing callers (e.g. the quick status-change UI) keep
     * working without updates. */

    @Size(max = 160)
    private String companyName;

    @Size(max = 40)
    private String lifecycleStage;

    @Size(max = 80)
    private String referralSource;

    @PositiveOrZero
    private Long lifetimeValueCents;

    @Size(max = 20)
    private String preferredContact;

    @Size(max = 4000)
    private String dietaryNotes;
}
