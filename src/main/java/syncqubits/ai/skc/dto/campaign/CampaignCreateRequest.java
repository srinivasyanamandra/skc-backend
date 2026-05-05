package syncqubits.ai.skc.dto.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    private UUID defaultTemplateId;

    private Map<String, Object> globalVariables;
}
