package syncqubits.ai.skc.dto.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientNoteRequest {

    @NotBlank @Size(max = 4000)
    private String body;

    @Size(max = 40)
    private String category;

    private Boolean pinned;
}
