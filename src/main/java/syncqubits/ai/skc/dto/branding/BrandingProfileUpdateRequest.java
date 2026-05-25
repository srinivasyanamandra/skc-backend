package syncqubits.ai.skc.dto.branding;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial-update payload for PUT /api/admin/branding. Every field is
 * optional; the service applies only non-null fields, so an admin can save
 * one section of the form at a time without nuking the rest of the profile.
 *
 * <p>Field-size caps mirror the column definitions on {@code BrandingProfile}
 * so validation fails fast at the controller boundary instead of bubbling
 * up as a Postgres "value too long" error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingProfileUpdateRequest {

    @Size(max = 160) private String brandName;
    @Size(max = 200) private String tagline;
    @Min(1800)       private Integer establishedYear;

    @Size(max = 20)  private String phonePrimary;
    @Size(max = 20)  private String phoneSecondary;
    @Email @Size(max = 255) private String email;
    @Size(max = 255) private String website;

    @Size(max = 200) private String addressLine1;
    @Size(max = 200) private String addressLine2;
    @Size(max = 80)  private String city;
    @Size(max = 80)  private String state;
    @Size(max = 20)  private String pincode;

    @Size(max = 16)  private String primaryColor;
    @Size(max = 16)  private String secondaryColor;
    @Size(max = 16)  private String accentColor;
    @Size(max = 16)  private String inkColor;

    @Size(max = 120) private String displayFont;
    @Size(max = 120) private String bodyFont;

    @Size(max = 32)  private String gstin;
    @Size(max = 40)  private String fssaiLicense;
    @Size(max = 40)  private String cin;
    @Size(max = 20)  private String panNumber;

    @Size(max = 4000) private String brandPromise;
    @Size(max = 4000) private String legalDisclaimer;

    @Size(max = 255) private String socialInstagram;
    @Size(max = 255) private String socialFacebook;
    @Size(max = 255) private String socialYoutube;
}
