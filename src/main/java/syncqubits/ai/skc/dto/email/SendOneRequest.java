package syncqubits.ai.skc.dto.email;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOneRequest {
    @NotNull
    private ResolveRequest.RecipientRef to;

    @NotNull
    private UUID templateId;

    private Map<String, Object> variables;
}
