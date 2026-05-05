package syncqubits.ai.skc.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendResult {
    private boolean success;
    private String messageId;
    private String error;
    private int attempts;
}
