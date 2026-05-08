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
public class ClientAddressResponse {
    private UUID id;
    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private Boolean primary;
    private Instant createdAt;
    private Instant updatedAt;
}
