package syncqubits.ai.skc.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    /** review_invitation | campaign | quote_confirmation | custom */
    @NotBlank
    @Size(max = 40)
    private String type;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @Size(max = 200)
    private String preheader;

    /** { html, text, blocks?, variables? } */
    @NotNull
    private Map<String, Object> content;

    private Boolean isActive;
}
