package syncqubits.ai.skc.dto.recipient;

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
public class ResolveRequest {
    private List<RecipientRef> include;
    private List<RecipientSegment> segments;
    private List<RecipientRef> exclude;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientRef {
        @NotNull
        /** "client" | "subscriber" */
        private String kind;
        @NotNull
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientSegment {
        @NotNull
        /** "clients" | "subscribers" */
        private String kind;
        /** allowed keys: status, since, until, eventType, minRating, active */
        private Map<String, Object> filter;
    }
}
