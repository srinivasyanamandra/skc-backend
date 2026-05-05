package syncqubits.ai.skc.dto.recipient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveResponse {
    private long totalUnique;
    private Map<String, Long> byKind;
    private long deduplicated;
    private long skippedInactive;
    private List<RecipientItem> preview;
}
