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
public class RecipientSearchResponse {
    private int page;
    private int size;
    private long total;
    private Map<String, Long> kindCounts;
    private List<RecipientItem> items;
}
