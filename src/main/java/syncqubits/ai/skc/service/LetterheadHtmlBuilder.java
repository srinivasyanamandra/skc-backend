package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import syncqubits.ai.skc.entity.BrandAsset;
import syncqubits.ai.skc.entity.BrandingProfile;
import syncqubits.ai.skc.repository.BrandAssetRepository;

import java.util.List;

/**
 * Produces the canonical letterhead HTML — the server-side mirror of the
 * React {@code LetterheadPreview} component.
 *
 * <p>Output is a fully self-contained HTML document (DOCTYPE + inline
 * styles + absolute image URLs) so it can be handed straight to Playwright
 * for PDF rendering. The layout, colors, fonts and footer structure
 * deliberately mirror the React preview so admins always see what they'll
 * get downloaded.
 *
 * <p>Template version: keep this aligned with {@code GeneratedDocument
 * .templateVersion} so future redesigns can coexist with already-rendered
 * documents. Bump to {@code v2} when the layout changes.
 */
@Component
@RequiredArgsConstructor
public class LetterheadHtmlBuilder {

    public static final String VERSION = "v1";

    private final BrandAssetRepository brandAssetRepository;

    /**
     * Render the letterhead.
     *
     * @param profile   current branding profile
     * @param publicBase absolute origin (e.g. {@code http://localhost:8080})
     *                   used to resolve relative asset URLs the browser
     *                   needs to fetch during PDF render
     * @param bodyHtml   optional body HTML; pass {@code null} for the
     *                   default "Document body renders here…" placeholder
     */
    public String build(BrandingProfile profile, String publicBase, String bodyHtml) {
        String primary = coalesce(profile.getPrimaryColor(), "#7a1d1d");
        String accent  = coalesce(profile.getAccentColor(),  "#c89b3c");
        String ink     = coalesce(profile.getInkColor(),     "#1a1a1a");
        String display = fontStack(profile.getDisplayFont(), true);
        String body    = fontStack(profile.getBodyFont(),    false);

        String brandName  = coalesce(profile.getBrandName(),  "Your Brand Name");
        String tagline    = coalesce(profile.getTagline(),    "Tagline goes here");
        String promise    = coalesce(profile.getBrandPromise(),
                "Pure Vegetarian Catering Crafted with Tradition & Excellence");
        Integer year      = profile.getEstablishedYear();
        String sinceLabel = year != null ? "Since " + year : null;

        String logoUrl = resolveLogoUrl(publicBase);
        String initials = computeInitials(brandName);
        String address  = joinAddress(profile);
        String phoneLine = joinNonBlank(" · ", profile.getPhonePrimary(), profile.getPhoneSecondary());

        StringBuilder sb = new StringBuilder(4096);

        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>").append(esc(brandName)).append(" — Letterhead</title>");
        appendFontImports(sb, profile.getDisplayFont(), profile.getBodyFont());
        sb.append("<style>");
        sb.append("@page { size: A4; margin: 0; }");
        sb.append("html, body { margin:0; padding:0; }");
        sb.append("body { font-family: ").append(body).append("; color: ").append(ink).append("; }");
        sb.append(".letterhead { width: 210mm; min-height: 297mm; display: flex; flex-direction: column; }");
        sb.append(".band { height: 6mm; background: linear-gradient(90deg, ").append(primary).append(", ").append(accent).append("); }");
        sb.append(".header { padding: 14mm 14mm 9mm; border-bottom: 1px solid ").append(accent).append("33; display: flex; align-items: flex-start; justify-content: space-between; gap: 10mm; }");
        sb.append(".brand-name { font-family: ").append(display).append("; font-size: 28pt; font-weight: 700; line-height: 1.05; color: ").append(primary).append("; letter-spacing: 0.4px; }");
        sb.append(".tagline { margin-top: 3mm; font-size: 9pt; letter-spacing: 1.4px; text-transform: uppercase; color: ").append(ink).append("; opacity: 0.7; }");
        sb.append(".since { margin-top: 4mm; display: inline-block; font-size: 7pt; letter-spacing: 1.6px; text-transform: uppercase; padding: 1mm 3mm; border: 1px solid ").append(accent).append("; color: ").append(accent).append("; border-radius: 1mm; }");
        sb.append(".logo { width: 24mm; height: 24mm; flex-shrink: 0; }");
        sb.append(".logo img { width:100%; height:100%; object-fit: contain; }");
        sb.append(".logo-fallback { width:100%; height:100%; border-radius:50%; border: 2px solid ").append(primary).append("; display:flex; align-items:center; justify-content:center; background: ").append(primary).append("0a; font-family: ").append(display).append("; font-size: 18pt; font-weight: 700; color: ").append(primary).append("; }");
        sb.append(".body { flex: 1; padding: 16mm 14mm; display:flex; align-items:center; justify-content:center; color: ").append(ink).append("66; font-size: 10pt; text-align:center; font-style: italic; }");
        sb.append(".body-frame { border: 1px dashed ").append(ink).append("33; padding: 10mm 8mm; border-radius: 2mm; width: 100%; }");
        sb.append(".footer { padding: 8mm 12mm 10mm; background: ").append(primary).append("0a; border-top: 2px solid ").append(primary).append("; font-size: 8pt; line-height: 1.5; }");
        sb.append(".promise { font-family: ").append(display).append("; font-size: 11pt; font-style: italic; color: ").append(primary).append("; text-align:center; margin-bottom: 5mm; }");
        sb.append(".grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 5mm; padding-top: 4mm; border-top: 1px solid ").append(accent).append("55; }");
        sb.append(".grid h4 { margin:0 0 2mm; font-weight:700; color: ").append(primary).append("; letter-spacing: 0.6px; font-size: 8pt; }");
        sb.append(".social { margin-top: 5mm; padding-top: 4mm; border-top: 1px solid ").append(accent).append("33; display:flex; justify-content:center; gap: 6mm; color: ").append(accent).append("; font-size: 8pt; }");
        sb.append(".legal { margin-top: 5mm; padding-top: 3mm; border-top: 1px solid ").append(accent).append("22; text-align:center; font-size: 7pt; opacity: 0.6; }");
        sb.append(".dim { opacity: 0.65; }");
        sb.append("</style></head><body>");

        sb.append("<div class=\"letterhead\">");
        sb.append("<div class=\"band\"></div>");

        // Header
        sb.append("<header class=\"header\">");
        sb.append("<div style=\"flex:1\">");
        sb.append("<div class=\"brand-name\">").append(esc(brandName)).append("</div>");
        sb.append("<div class=\"tagline\">").append(esc(tagline)).append("</div>");
        if (sinceLabel != null) {
            sb.append("<div class=\"since\">").append(esc(sinceLabel)).append("</div>");
        }
        sb.append("</div>");
        sb.append("<div class=\"logo\">");
        if (logoUrl != null) {
            sb.append("<img src=\"").append(esc(logoUrl)).append("\" alt=\"\">");
        } else {
            sb.append("<div class=\"logo-fallback\">").append(esc(initials)).append("</div>");
        }
        sb.append("</div>");
        sb.append("</header>");

        // Body
        sb.append("<div class=\"body\"><div class=\"body-frame\">");
        sb.append(bodyHtml != null && !bodyHtml.isBlank()
                ? bodyHtml
                : "Document body renders here — menu, invoice, proposal or PO content.");
        sb.append("</div></div>");

        // Footer
        sb.append("<footer class=\"footer\">");
        sb.append("<div class=\"promise\">&ldquo;").append(esc(promise)).append("&rdquo;</div>");
        sb.append("<div class=\"grid\">");

        sb.append("<div><h4>CONTACT</h4>");
        if (notBlank(phoneLine))      sb.append("<div>").append(esc(phoneLine)).append("</div>");
        if (notBlank(profile.getEmail()))   sb.append("<div>").append(esc(profile.getEmail())).append("</div>");
        if (notBlank(profile.getWebsite())) sb.append("<div>").append(esc(profile.getWebsite())).append("</div>");
        sb.append("</div>");

        sb.append("<div><h4>ADDRESS</h4>");
        sb.append("<div>").append(esc(notBlank(address) ? address : "—")).append("</div>");
        sb.append("</div>");

        sb.append("<div><h4>COMPLIANCE</h4>");
        boolean anyCompliance = false;
        if (notBlank(profile.getGstin()))        { appendKv(sb, "GSTIN", profile.getGstin());     anyCompliance = true; }
        if (notBlank(profile.getFssaiLicense())) { appendKv(sb, "FSSAI", profile.getFssaiLicense()); anyCompliance = true; }
        if (notBlank(profile.getCin()))          { appendKv(sb, "CIN",   profile.getCin());        anyCompliance = true; }
        if (notBlank(profile.getPanNumber()))    { appendKv(sb, "PAN",   profile.getPanNumber());  anyCompliance = true; }
        if (!anyCompliance) sb.append("<div class=\"dim\">—</div>");
        sb.append("</div>");

        sb.append("</div>"); // /grid

        // Social row
        StringBuilder social = new StringBuilder();
        if (notBlank(profile.getSocialInstagram())) social.append("<span>Instagram</span>");
        if (notBlank(profile.getSocialFacebook()))  social.append("<span>Facebook</span>");
        if (notBlank(profile.getSocialYoutube()))   social.append("<span>YouTube</span>");
        if (social.length() > 0) {
            sb.append("<div class=\"social\">").append(social).append("</div>");
        }

        if (notBlank(profile.getLegalDisclaimer())) {
            sb.append("<div class=\"legal\">").append(esc(profile.getLegalDisclaimer())).append("</div>");
        }

        sb.append("</footer>");
        sb.append("</div></body></html>");

        return sb.toString();
    }

    /* ─────────────────────────── internals ─────────────────────────────── */

    /** Pick the most-recent PRIMARY_LOGO asset, if one exists. */
    private String resolveLogoUrl(String publicBase) {
        List<BrandAsset> logos = brandAssetRepository.findByRoleOrderByCreatedAtDesc("PRIMARY_LOGO");
        if (logos.isEmpty()) return null;
        String url = logos.get(0).getPublicUrl();
        if (url == null) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String base = publicBase == null ? "" : publicBase.replaceAll("/+$", "");
        return base + url;
    }

    private static void appendFontImports(StringBuilder sb, String displayFont, String bodyFont) {
        /* Google Fonts is available in 99% of cases. Embed the @import so
           Playwright fetches the font during render — no manual font file
           juggling required. Sanitise the family name (alphanumeric + space
           only) to avoid breaking the URL. */
        String d = sanitizeFontFamily(coalesce(displayFont, "Playfair Display"));
        String b = sanitizeFontFamily(coalesce(bodyFont,    "Inter"));
        sb.append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
        sb.append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2")
          .append("?family=").append(d.replace(' ', '+')).append(":wght@400;700")
          .append("&family=").append(b.replace(' ', '+')).append(":wght@400;600")
          .append("&display=swap\" rel=\"stylesheet\">");
    }

    private static String fontStack(String family, boolean isDisplay) {
        String fb = isDisplay ? "Georgia, serif" : "system-ui, -apple-system, sans-serif";
        if (family == null || family.isBlank()) {
            return isDisplay ? "\"Playfair Display\", " + fb : "\"Inter\", " + fb;
        }
        return "\"" + sanitizeFontFamily(family) + "\", " + fb;
    }

    private static String sanitizeFontFamily(String name) {
        return name.replaceAll("[^A-Za-z0-9 ]", "").trim();
    }

    private static String computeInitials(String brand) {
        if (brand == null || brand.isBlank()) return "BR";
        String[] words = brand.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (out.length() >= 2) break;
            if (!w.isEmpty()) out.append(Character.toUpperCase(w.charAt(0)));
        }
        return out.length() == 0 ? "BR" : out.toString();
    }

    private static String joinAddress(BrandingProfile p) {
        String cityLine = joinNonBlank(" · ", p.getCity(), p.getState(), p.getPincode());
        return joinNonBlank(", ", p.getAddressLine1(), p.getAddressLine2(), cityLine);
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String k, String v) {
        sb.append("<div><span class=\"dim\">").append(k).append(":</span> ")
          .append(esc(v)).append("</div>");
    }

    private static String coalesce(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    /** Minimal HTML-attribute/text escaper. Sufficient for our admin-only
     *  input surface where the values come from authenticated POSTs. */
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> out.append("&amp;");
                case '<'  -> out.append("&lt;");
                case '>'  -> out.append("&gt;");
                case '"'  -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }
}
