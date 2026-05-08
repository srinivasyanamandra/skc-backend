package syncqubits.ai.skc.dto.client;

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
public class ClientNoteResponse {
    private UUID id;
    private String body;
    private String category;
    private Boolean pinned;
    private String authorEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
