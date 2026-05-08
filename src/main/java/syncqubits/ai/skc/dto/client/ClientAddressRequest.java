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
public class ClientAddressRequest {

    @Size(max = 40)
    private String label;

    @NotBlank @Size(max = 200)
    private String line1;

    @Size(max = 200) private String line2;
    @Size(max = 80)  private String city;
    @Size(max = 80)  private String state;
    @Size(max = 20)  private String pincode;

    private Boolean primary;
}
