package syncqubits.ai.skc.dto.vendor;

import jakarta.validation.constraints.Email;
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
public class VendorContactRequest {
    @NotBlank @Size(max = 120) private String name;
    @Size(max = 80)            private String role;
    @Size(max = 20)            private String phone;
    @Email @Size(max = 255)    private String email;
                               private Boolean primary;
    @Size(max = 4000)          private String notes;
}
