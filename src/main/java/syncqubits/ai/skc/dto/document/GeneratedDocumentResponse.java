package syncqubits.ai.skc.dto.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneratedDocumentResponse {
    private UUID id;
    private String documentType;
    private String documentNumber;
    private String pdfFilename;
    private String pdfUrl;
    private Long sizeBytes;
    private String templateVersion;
    private String renderedByEmail;
    private Instant createdAt;
}
