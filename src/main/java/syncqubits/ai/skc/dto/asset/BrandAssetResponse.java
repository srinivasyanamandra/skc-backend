package syncqubits.ai.skc.dto.asset;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Wire shape for a single brand asset returned by the Assets endpoints. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandAssetResponse {
    private UUID id;
    private String filename;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String role;
    private String altText;
    private String publicUrl;
    private Instant createdAt;
}
