package syncqubits.ai.skc.dto.campaign;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignPreviewRequest {
    /** Render for one specific recipient. Mutually exclusive with sample. */
    private ResolveRequest.RecipientRef recipient;

    /** Render for N random recipients out of the resolved list. */
    @Min(1)
    private Integer sample;
}
