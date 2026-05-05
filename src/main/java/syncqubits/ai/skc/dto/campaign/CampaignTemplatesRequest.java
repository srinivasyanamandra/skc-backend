package syncqubits.ai.skc.dto.campaign;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTemplatesRequest {

    @NotNull
    private UUID defaultTemplateId;

    /** keys: "client" | "subscriber" → templateId */
    private Map<String, UUID> byKindTemplate;

    private List<PerRecipient> perRecipient;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerRecipient {
        /** "client" | "subscriber" */
        @NotNull
        private String kind;
        /** Accepts UUID string — parsed explicitly to handle frontend string split. */
        @NotNull
        private String id;
        @NotNull
        private UUID templateId;
        private Map<String, Object> variables;

        public UUID getIdAsUuid() {
            return UUID.fromString(id);
        }
    }
}
