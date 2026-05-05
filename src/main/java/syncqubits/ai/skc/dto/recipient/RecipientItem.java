package syncqubits.ai.skc.dto.recipient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientItem {
    /** "client" | "subscriber" */
    private String kind;
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private List<String> tags;
    private LocalDate lastEventDate;
    private Instant createdAt;
}
