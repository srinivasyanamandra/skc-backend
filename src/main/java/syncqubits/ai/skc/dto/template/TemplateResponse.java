package syncqubits.ai.skc.dto.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    private UUID id;
    private String name;
    private String code;
    private String type;
    private String subject;
    private String preheader;
    private Map<String, Object> content;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
