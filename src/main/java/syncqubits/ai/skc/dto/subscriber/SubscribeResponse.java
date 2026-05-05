package syncqubits.ai.skc.dto.subscriber;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeResponse {
    private Boolean subscribed;
    private String email;
}
