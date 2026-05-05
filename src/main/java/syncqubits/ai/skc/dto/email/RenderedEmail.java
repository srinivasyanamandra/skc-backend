package syncqubits.ai.skc.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderedEmail {
    private String subject;
    private String preheader;
    private String html;
    private String text;
    private Map<String, Object> variablesResolved;
}
