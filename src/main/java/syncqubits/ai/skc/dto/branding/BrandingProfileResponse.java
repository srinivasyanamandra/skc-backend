package syncqubits.ai.skc.dto.branding;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape returned by GET /api/admin/branding and PUT /api/admin/branding.
 * Mirrors {@code BrandingProfile} 1:1 — admin form is the only consumer and
 * benefits from a flat structure that maps straight into form state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandingProfileResponse {

    private UUID id;

    private String brandName;
    private String tagline;
    private Integer establishedYear;

    private String phonePrimary;
    private String phoneSecondary;
    private String email;
    private String website;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;

    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String inkColor;

    private String displayFont;
    private String bodyFont;

    private String gstin;
    private String fssaiLicense;
    private String cin;
    private String panNumber;

    private String brandPromise;
    private String legalDisclaimer;

    private String socialInstagram;
    private String socialFacebook;
    private String socialYoutube;

    private Instant createdAt;
    private Instant updatedAt;
}
