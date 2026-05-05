package syncqubits.ai.skc.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureRequest {
    /** If null, the current state is toggled. */
    private Boolean featured;
}
