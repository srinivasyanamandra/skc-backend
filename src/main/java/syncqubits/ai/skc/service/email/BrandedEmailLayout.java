package syncqubits.ai.skc.service.email;

/**
 * Centralised, email-safe branded layout for transactional emails.
 *
 * Wraps an inner HTML body fragment with the standard Sri Karthikeya Caterers
 * header (logo wordmark + saffron accent rule) and footer (brand line, address,
 * unsubscribe pointer, copyright). Uses table-based layout, inline styles, and
 * web-safe font stacks so it renders consistently across Gmail, Outlook, Apple
 * Mail, and webmail clients that strip {@code <style>} blocks.
 *
 * <p>Reusable across every transactional template — services that need the
 * shared chrome should compose: {@code BrandedEmailLayout.wrap(preheader, inner)}.
 *
 * <p>Brand tokens kept in sync with {@code src/tokens.css}:
 * <ul>
 *   <li>{@code --color-primary}  → {@value #COLOR_PRIMARY}  (deep forest)</li>
 *   <li>{@code --color-accent}   → {@value #COLOR_ACCENT}   (saffron)</li>
 *   <li>{@code --color-bg-warm}  → {@value #COLOR_BG_WARM}  (warm ivory)</li>
 *   <li>{@code --color-surface}  → {@value #COLOR_SURFACE}  (card white)</li>
 *   <li>{@code --color-border}   → {@value #COLOR_BORDER}   (warm tan)</li>
 *   <li>{@code --color-ink}      → {@value #COLOR_INK}      (body ink)</li>
 *   <li>{@code --color-muted}    → {@value #COLOR_MUTED}    (muted grey)</li>
 * </ul>
 */
public final class BrandedEmailLayout {

    public static final String COLOR_PRIMARY  = "#143a26";
    public static final String COLOR_ACCENT   = "#c9882f";
    public static final String COLOR_BG_WARM  = "#fdf7ec";
    public static final String COLOR_SURFACE  = "#ffffff";
    public static final String COLOR_BORDER   = "#ecdfc4";
    public static final String COLOR_INK      = "#1a1a1a";
    public static final String COLOR_MUTED    = "#7a7a7a";

    public static final String BRAND_NAME     = "Sri Karthikeya Caterers";
    public static final String BRAND_TAGLINE  = "Pure-vegetarian catering, served with quiet excellence.";
    public static final String BRAND_ADDRESS  = "Hyderabad, Telangana, India";

    private BrandedEmailLayout() {}

    /**
     * Wrap an inner HTML fragment in the branded chrome.
     *
     * @param preheader hidden inbox-preview text (60–110 chars ideal). Pass an
     *                  empty string to suppress.
     * @param innerHtml the body fragment — typically an {@code <h1>} + paragraphs.
     *                  Must already be safe HTML (no untrusted user input).
     * @return a complete HTML document.
     */
    public static String wrap(String preheader, String innerHtml) {
        String safePreheader = preheader == null ? "" : preheader;
        String safeInner = innerHtml == null ? "" : innerHtml;

        return "<!doctype html>"
            + "<html lang=\"en\"><head>"
            + "<meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<meta name=\"x-apple-disable-message-reformatting\">"
            + "<title>" + BRAND_NAME + "</title>"
            + "</head>"
            + "<body style=\"margin:0;padding:0;background:" + COLOR_BG_WARM + ";"
            +   "font-family:Inter,Helvetica,Arial,sans-serif;color:" + COLOR_INK + ";\">"

            // Hidden preheader — consumed by Gmail/Apple Mail inbox preview.
            + "<span style=\"display:none!important;visibility:hidden;opacity:0;"
            +   "color:transparent;height:0;width:0;overflow:hidden;mso-hide:all;\">"
            +   safePreheader
            + "</span>"

            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            +   "style=\"background:" + COLOR_BG_WARM + ";padding:32px 16px;\">"
            +   "<tr><td align=\"center\">"

            +     "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
            +       "style=\"max-width:600px;width:100%;\">"

            // ─── Header ───────────────────────────────────────────────
            +       "<tr><td align=\"center\" style=\"padding:8px 0 20px;\">"
            +         "<div style=\"font-family:Fraunces,Georgia,serif;font-size:22px;"
            +           "font-weight:500;color:" + COLOR_PRIMARY + ";letter-spacing:0.01em;\">"
            +           BRAND_NAME
            +         "</div>"
            +         "<div style=\"width:48px;height:3px;background:" + COLOR_ACCENT + ";"
            +           "margin:10px auto 0;border-radius:2px;\"></div>"
            +       "</td></tr>"

            // ─── Body card ────────────────────────────────────────────
            +       "<tr><td style=\"background:" + COLOR_SURFACE + ";"
            +         "border:1px solid " + COLOR_BORDER + ";border-radius:14px;"
            +         "padding:40px 40px 36px;\">"
            +         safeInner
            +       "</td></tr>"

            // ─── Footer ───────────────────────────────────────────────
            +       "<tr><td align=\"center\" style=\"padding:24px 16px 8px;"
            +         "color:" + COLOR_MUTED + ";font-size:12px;line-height:1.6;\">"
            +         "<div style=\"font-family:Fraunces,Georgia,serif;font-size:14px;"
            +           "color:" + COLOR_PRIMARY + ";margin-bottom:6px;\">"
            +           BRAND_NAME
            +         "</div>"
            +         "<div>" + BRAND_TAGLINE + "</div>"
            +         "<div style=\"margin-top:6px;\">" + BRAND_ADDRESS + "</div>"
            +         "<div style=\"margin-top:14px;\">"
            +           "You are receiving this email because you subscribed at "
            +           "<a href=\"https://srikarthikeyacaterers.com\" "
            +             "style=\"color:" + COLOR_ACCENT + ";text-decoration:none;\">"
            +             "srikarthikeyacaterers.com"
            +           "</a>."
            +         "</div>"
            +         "<div style=\"margin-top:6px;\">"
            +           "If you'd prefer not to hear from us, simply reply with "
            +           "<em>unsubscribe</em> and we'll remove you right away."
            +         "</div>"
            +         "<div style=\"margin-top:14px;color:#a8a8a8;\">"
            +           "&copy; {{year}} " + BRAND_NAME + ". All rights reserved."
            +         "</div>"
            +       "</td></tr>"

            +     "</table>"
            +   "</td></tr>"
            + "</table>"
            + "</body></html>";
    }

    /**
     * Plain-text equivalent of the branded chrome — used for the {@code text/plain}
     * MIME alternative so spam filters and accessibility readers see meaningful copy.
     */
    public static String wrapText(String innerText) {
        String inner = innerText == null ? "" : innerText;
        return BRAND_NAME + "\n"
            + "------------------------------\n\n"
            + inner.trim() + "\n\n"
            + "------------------------------\n"
            + BRAND_NAME + " — " + BRAND_TAGLINE + "\n"
            + BRAND_ADDRESS + "\n"
            + "https://srikarthikeyacaterers.com\n\n"
            + "If you'd prefer not to hear from us, simply reply with 'unsubscribe'.\n"
            + "© {{year}} " + BRAND_NAME + ". All rights reserved.\n";
    }
}
