package syncqubits.ai.skc.dto.vendor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial-update — every field is optional; only non-null fields are applied. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorUpdateRequest {

    @Size(max = 160) private String name;
    @Size(max = 40)  private String category;
    @Size(max = 20)  private String status;

    @Size(max = 120) private String primaryContactName;
    @Size(max = 20)  private String primaryContactPhone;
    @Email @Size(max = 255) private String primaryContactEmail;

    @Size(max = 32) private String gstNumber;
    @Size(max = 20) private String panNumber;
    @Size(max = 255) private String website;

    @Size(max = 200) private String addressLine1;
    @Size(max = 200) private String addressLine2;
    @Size(max = 80)  private String city;
    @Size(max = 80)  private String state;
    @Size(max = 20)  private String pincode;

    @Size(max = 40) private String paymentTerms;
    @Size(max = 40) private String preferredPayment;
    @Size(max = 3)  private String currency;

    @Size(max = 4000) private String notes;
}
