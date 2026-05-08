package syncqubits.ai.skc.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manual client creation request. Distinct from {@link ClientUpdateRequest}
 * because the create flow has different invariants — name/email/phone are
 * mandatory, status defaults to LEAD if not supplied, and the source is
 * recorded as "manual" so the dashboard can split organic-vs-imported leads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCreateRequest {

    @NotBlank @Size(max = 120)
    private String name;

    @NotBlank @Email @Size(max = 255)
    private String email;

    @NotBlank @Size(max = 20)
    private String phone;

    @Size(max = 40)
    private String status;

    @Size(max = 40)
    private String lifecycleStage;

    @Size(max = 160)
    private String companyName;

    @Size(max = 80)
    private String referralSource;

    @Size(max = 20)
    private String preferredContact;

    @Size(max = 4000)
    private String notes;

    /** Optional source tag — defaults to "manual" when blank. */
    @Size(max = 40)
    private String source;
}
