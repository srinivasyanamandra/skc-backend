package syncqubits.ai.skc.dto.campaign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignRecipientsRequest {
    /** "append" | "replace" — defaults to replace. */
    private String mode;

    private List<ResolveRequest.RecipientRef> include;
    private List<ResolveRequest.RecipientSegment> segments;
    private List<ResolveRequest.RecipientRef> exclude;

    public ResolveRequest toResolveRequest() {
        return ResolveRequest.builder()
                .include(include)
                .segments(segments)
                .exclude(exclude)
                .build();
    }
}
