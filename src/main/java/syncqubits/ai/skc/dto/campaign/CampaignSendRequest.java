package syncqubits.ai.skc.dto.campaign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSendRequest {
    /** ISO-8601; null = send now. */
    private Instant scheduleAt;

    /** Optional throttle override: { "perMinute": 120 } */
    private Map<String, Object> throttle;
}
