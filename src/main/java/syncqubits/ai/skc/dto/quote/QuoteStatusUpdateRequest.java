package syncqubits.ai.skc.dto.quote;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteStatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status;

    private String note;
}
