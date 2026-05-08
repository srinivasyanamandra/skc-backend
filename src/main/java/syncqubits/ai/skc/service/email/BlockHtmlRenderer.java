package syncqubits.ai.skc.service.email;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Renders the block-based template structure (the same shape the
 * EmailBuilder UI saves under {@code content.blocks}) into the final
 * email HTML — server-side, with raw {@code {{var}}} placeholders
 * preserved so {@code EmailService.substitute} can resolve them at send
 * time.
 *
 * Why this lives on the backend:
 *   The frontend exporter used to inline a "fill sample values" pass
 *   when generating {@code content.html}, which baked
 *   {@code {{firstName}}} → {@code [First Name]} into stored HTML and
 *   left the backend nothing to substitute. Templates persisted under
 *   that bug still have placeholder-free HTML — but their
 *   {@code content.blocks} array is intact (the editor never mutates
 *   block text). By re-rendering from blocks at send time, every
 *   template — old or new — round-trips placeholders correctly.
 *
 * The visual styling here mirrors the frontend exporter byte-for-byte
 * so previews and sent emails look identical.
 */
@Component
public class BlockHtmlRenderer {

    /** Produce a sendable HTML body from a list of editor blocks.
     *  Returns an empty string if {@code blocks} is null/empty. */
    public String renderBody(List<Map<String, Object>> blocks) {
        if (blocks == null || blocks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> b : blocks) {
            String type = str(b.get("type"));
            String text = str(b.get("text"));
            switch (type == null ? "" : type) {
                case "heading" -> sb.append(
                        "<h1 style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:28px;font-weight:400;letter-spacing:-0.02em;margin:0 0 14px;\">")
                        .append(inline(text)).append("</h1>\n");
                case "subheading" -> sb.append(
                        "<h3 style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:18px;font-weight:400;margin:14px 0 8px;\">")
                        .append(inline(text)).append("</h3>\n");
                case "paragraph" -> sb.append(
                        "<p style=\"margin:0 0 14px;color:#4f5147;line-height:1.7;font-size:15px;\">")
                        .append(inline(text)).append("</p>\n");
                case "button" -> {
                    String url = str(b.get("url"));
                    String label = text == null || text.isBlank() ? "Click here" : text;
                    sb.append(
                        "<p style=\"margin:8px 0 18px;\"><a href=\"")
                        .append(url == null || url.isBlank() ? "#" : url)
                        .append("\" style=\"display:inline-block;background:#c9882f;color:#fff;text-decoration:none;font-weight:600;padding:14px 28px;border-radius:999px;\">")
                        .append(label).append("</a></p>\n");
                }
                case "quote" -> sb.append(
                        "<blockquote style=\"border-left:3px solid #c9882f;padding:4px 0 4px 18px;margin:4px 0 18px;font-family:Fraunces,Georgia,serif;font-style:italic;color:#1a1a17;font-size:17px;line-height:1.55;\">")
                        .append(inline(text)).append("</blockquote>\n");
                case "divider" -> sb.append("<hr style=\"height:1px;background:#e6dfd1;border:0;margin:22px 0;\"/>\n");
                case "spacer"  -> sb.append("<div style=\"height:16px;line-height:16px;\">&nbsp;</div>\n");
                case "image" -> {
                    String url = str(b.get("url"));
                    if (url != null && !url.isBlank()) {
                        String alt = str(b.get("alt"));
                        sb.append("<img src=\"").append(url)
                          .append("\" alt=\"").append(alt == null ? "" : alt)
                          .append("\" style=\"display:block;width:100%;height:auto;border-radius:6px;margin:6px 0 18px;\"/>\n");
                    }
                }
                default -> { /* unknown block — skip silently */ }
            }
        }
        return sb.toString();
    }

    /**
     * Wrap a body fragment in the branded shell (header + content panel +
     * footer). Subject and preheader are rendered inline; both pre-headers
     * use the same hidden trick that hides them from the visible body but
     * lets inboxes preview them.
     */
    public String wrapBranded(String body, String subject, String preheader) {
        String safeSubject = subject == null ? "" : subject;
        String hiddenPreheader = preheader == null || preheader.isBlank()
                ? ""
                : "<div style=\"display:none;max-height:0;overflow:hidden;color:#ebe4d4;\">" + preheader + "</div>";
        return "<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
                + safeSubject + "</title></head>\n"
                + "<body style=\"margin:0;padding:0;background:#ebe4d4;font-family:Inter,-apple-system,sans-serif;color:#1a1a17;\">\n"
                + hiddenPreheader + "\n"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background:#ebe4d4;padding:28px 14px;\">\n"
                + "<tr><td align=\"center\">\n"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"600\" style=\"max-width:600px;background:#ffffff;border-radius:6px;overflow:hidden;\">\n"
                + "<tr><td style=\"background:linear-gradient(135deg,#061811 0%,#143a26 100%);padding:28px 32px;text-align:center;\">\n"
                + "<div style=\"font-family:Fraunces,Georgia,serif;font-size:22px;color:#fff;\">" + BrandedEmailLayout.BRAND_NAME + "</div>\n"
                + "<div style=\"font-size:11px;letter-spacing:0.18em;text-transform:uppercase;color:rgba(255,255,255,0.7);margin-top:6px;\">Pure Vegetarian · Hyderabad</div>\n"
                + "</td></tr>\n"
                + "<tr><td style=\"padding:32px;\">" + (body == null ? "" : body) + "</td></tr>\n"
                + "<tr><td style=\"background:#faf7f1;padding:24px 32px;border-top:1px solid #e6dfd1;text-align:center;font-size:12px;color:#7c7e74;line-height:1.6;\">\n"
                + "<p style=\"margin:0 0 6px;\"><strong style=\"color:#143a26;\">" + BrandedEmailLayout.BRAND_NAME + "</strong></p>\n"
                + "</td></tr>\n"
                + "</table>\n"
                + "</td></tr></table>\n"
                + "</body></html>";
    }

    /** Plain-text version: concatenated block text, blank-line separated.
     *  Used as the multipart text/plain leg. */
    public String renderText(List<Map<String, Object>> blocks) {
        if (blocks == null || blocks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> b : blocks) {
            String text = str(b.get("text"));
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /* ────────────────────────── helpers ────────────────────────── */

    /** Match the frontend's `renderInline` — newlines become <br>. The text
     *  is otherwise rendered as-is so {@code {{var}}} placeholders survive
     *  to {@code EmailService.substitute}. */
    private static String inline(String text) {
        return text == null ? "" : text.replace("\n", "<br>");
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
