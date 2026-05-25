package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Brand kit driving every Document Studio output (letterheads, menus,
 * invoices, POs, proposals).
 *
 * <p>Singleton-by-convention: the {@code BrandingProfileService} ensures
 * exactly one row exists via get-or-create. Exposing list/delete endpoints
 * for this would invite drift; document templates resolve to "the" profile
 * by always reading the most recent row.
 *
 * <p>Asset references (primary logo, monogram, watermark, signature images,
 * QR codes) are deliberately omitted in Phase 1 of the Document Studio
 * build. They join in Slice 3 once the asset uploader exists and there's
 * something for the form to reference.
 */
@Entity
@Table(name = "branding_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /* ── identity ───────────────────────────────────────────────────────── */

    @Column(name = "brand_name",       length = 160) private String brandName;
    @Column(length = 200)                            private String tagline;
    @Column(name = "established_year")               private Integer establishedYear;

    /* ── contact ────────────────────────────────────────────────────────── */

    @Column(name = "phone_primary",   length = 20)  private String phonePrimary;
    @Column(name = "phone_secondary", length = 20)  private String phoneSecondary;
    @Column(length = 255)                           private String email;
    @Column(length = 255)                           private String website;

    /* ── address ────────────────────────────────────────────────────────── */

    @Column(name = "address_line1", length = 200) private String addressLine1;
    @Column(name = "address_line2", length = 200) private String addressLine2;
    @Column(length = 80)                          private String city;
    @Column(length = 80)                          private String state;
    @Column(length = 20)                          private String pincode;

    /* ── visual identity ────────────────────────────────────────────────── */

    @Column(name = "primary_color",   length = 16) private String primaryColor;
    @Column(name = "secondary_color", length = 16) private String secondaryColor;
    @Column(name = "accent_color",    length = 16) private String accentColor;
    @Column(name = "ink_color",       length = 16) private String inkColor;

    @Column(name = "display_font", length = 120) private String displayFont;
    @Column(name = "body_font",    length = 120) private String bodyFont;

    /* ── compliance ─────────────────────────────────────────────────────── */

    @Column(length = 32)                          private String gstin;
    @Column(name = "fssai_license", length = 40)  private String fssaiLicense;
    @Column(length = 40)                          private String cin;
    @Column(name = "pan_number",    length = 20)  private String panNumber;

    /* ── narrative ──────────────────────────────────────────────────────── */

    @Column(name = "brand_promise",    columnDefinition = "TEXT") private String brandPromise;
    @Column(name = "legal_disclaimer", columnDefinition = "TEXT") private String legalDisclaimer;

    /* ── social ─────────────────────────────────────────────────────────── */

    @Column(name = "social_instagram", length = 255) private String socialInstagram;
    @Column(name = "social_facebook",  length = 255) private String socialFacebook;
    @Column(name = "social_youtube",   length = 255) private String socialYoutube;

    /* ── audit ──────────────────────────────────────────────────────────── */

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
