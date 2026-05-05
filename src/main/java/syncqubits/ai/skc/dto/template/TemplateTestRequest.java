package syncqubits.ai.skc.dto.template;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTestRequest {
    @NotBlank
    @Email
    private String to;

    private String name;

    /** Optional variable map merged into the render context. */
    private Map<String, Object> variables;
}
