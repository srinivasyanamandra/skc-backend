package syncqubits.ai.skc.service;

import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.service.email.BrandedEmailLayout;

import java.util.Map;

/**
 * Hardcoded fallback templates used when the {@code email_templates} table
 * has no active row for the requested type. Lets the system send invites,
 * quote-confirmations, etc. out of the box on a fresh install.
 *
 * Variables supported (via {@code {{var}}} substitution): clientName, eventType,
 * eventDate, reviewLink, expiresAt, brand.
 */
public final class DefaultTemplates {

    private DefaultTemplates() {}

    public static EmailTemplate reviewInvitation() {
        return EmailTemplate.builder()
                .name("__default_review_invitation__")
                .type(EmailTemplate.TemplateType.REVIEW_INVITATION)
                .subject("Share your experience with Sri Karthikeya Caterers")
                .preheader("A quick note from Sri Karthikeya Caterers — we'd love to hear your feedback.")
                .isActive(true)
                .content(Map.of(
                        "html", REVIEW_INVITATION_HTML,
                        "text", REVIEW_INVITATION_TEXT,
                        "variables", java.util.List.of(
                                "clientName", "eventType", "eventDate", "reviewLink", "expiresAt"
                        )
                ))
                .build();
    }

    /**
     * Hard-coded fallback for the newsletter "Thank you for subscribing" email.
     * Resolution order in services: code {@code SUBSCRIBE} →
     * type {@code SUBSCRIBE_THANK_YOU} → this default.
     *
     * Variables: {@code clientName}, {@code firstName}, {@code email},
     * {@code brand}, {@code year}.
     */
    public static EmailTemplate subscribeThankYou() {
        return EmailTemplate.builder()
                .name("__default_subscribe_thank_you__")
                .code("SUBSCRIBE")
                .type(EmailTemplate.TemplateType.SUBSCRIBE_THANK_YOU)
                .subject("Thank you for subscribing — Sri Karthikeya Caterers")
                .preheader("Welcome to our circle. Seasonal menus and festive specials, occasionally and never overwhelmingly.")
                .isActive(true)
                .content(Map.of(
                        "html", BrandedEmailLayout.wrap(
                                "Welcome to our circle. Seasonal menus and festive specials, occasionally and never overwhelmingly.",
                                SUBSCRIBE_INNER_HTML),
                        "text", BrandedEmailLayout.wrapText(SUBSCRIBE_INNER_TEXT),
                        "variables", java.util.List.of(
                                "clientName", "firstName", "email", "brand", "year"
                        )
                ))
                .build();
    }

    public static EmailTemplate quoteConfirmation() {
        return EmailTemplate.builder()
                .name("__default_quote_confirmation__")
                .type(EmailTemplate.TemplateType.QUOTE_CONFIRMATION)
                .subject("Thank you for your enquiry — Sri Karthikeya Caterers")
                .preheader("We've received your quote request and our team will reply within 24 hours.")
                .isActive(true)
                .content(Map.of(
                        "html", QUOTE_CONFIRMATION_HTML,
                        "text", QUOTE_CONFIRMATION_TEXT,
                        "variables", java.util.List.of("clientName", "eventType", "eventDate")
                ))
                .build();
    }

    /* ----------------------------- HTML / text content ----------------------------- */

    private static final String SUBSCRIBE_INNER_HTML =
            "<h1 style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:28px;"
            +   "font-weight:400;line-height:1.25;margin:0 0 12px;letter-spacing:-0.01em;\">"
            +   "Welcome, {{firstName}}."
            + "</h1>"
            + "<p style=\"color:#5b5b5b;font-size:14px;margin:0 0 28px;\">"
            +   "You're now part of our circle."
            + "</p>"
            + "<p style=\"font-size:16px;line-height:1.7;margin:0 0 18px;\">"
            +   "Thank you for subscribing to {{brand}}. We're delighted to keep you "
            +   "in the loop with seasonal menus, festive specials, and the occasional "
            +   "story from our kitchen — sent only when there is something genuinely "
            +   "worth sharing."
            + "</p>"
            + "<p style=\"font-size:16px;line-height:1.7;margin:0 0 28px;\">"
            +   "If you're planning an event soon, we would be honoured to be a part "
            +   "of it. Reply to this email or reach us at any time — we read every "
            +   "message ourselves."
            + "</p>"

            // Accent rule + CTA
            + "<div style=\"width:48px;height:2px;background:#c9882f;margin:0 0 24px;\"></div>"
            + "<p style=\"text-align:left;margin:0 0 28px;\">"
            +   "<a href=\"https://srikarthikeyacaterers.com/#menus\" "
            +     "style=\"display:inline-block;background:#c9882f;color:#ffffff;"
            +     "text-decoration:none;padding:13px 26px;border-radius:8px;"
            +     "font-weight:600;font-size:15px;letter-spacing:0.02em;\">"
            +     "Explore our menus"
            +   "</a>"
            + "</p>"

            + "<p style=\"color:#7a7a7a;font-size:13px;line-height:1.6;margin:0;\">"
            +   "With warm regards,<br>"
            +   "<span style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:15px;\">"
            +     "The {{brand}} family"
            +   "</span>"
            + "</p>";

    private static final String SUBSCRIBE_INNER_TEXT =
            "Welcome, {{firstName}}.\n"
            + "You're now part of our circle.\n\n"
            + "Thank you for subscribing to {{brand}}. We'll keep you in the loop with\n"
            + "seasonal menus, festive specials, and the occasional story from our\n"
            + "kitchen — sent only when there is something worth sharing.\n\n"
            + "If you're planning an event soon, we'd be honoured to be a part of it.\n"
            + "Reply to this email or reach us anytime.\n\n"
            + "Explore our menus: https://srikarthikeyacaterers.com/#menus\n\n"
            + "With warm regards,\n"
            + "The {{brand}} family";


    private static final String REVIEW_INVITATION_HTML =
            "<!doctype html>" +
            "<html><body style=\"margin:0;padding:0;background:#fdf7ec;font-family:Inter,Arial,sans-serif;color:#1a1a1a;\">" +
            "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fdf7ec;padding:32px 0;\">" +
            "    <tr><td align=\"center\">" +
            "      <table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff;border:1px solid #ecdfc4;border-radius:12px;padding:36px 40px;\">" +
            "        <tr><td>" +
            "          <h1 style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:26px;font-weight:400;margin:0 0 8px;letter-spacing:-0.01em;\">" +
            "            A short note of thanks, {{clientName}}." +
            "          </h1>" +
            "          <p style=\"color:#5b5b5b;font-size:14px;margin:0 0 24px;\">" +
            "            It was our privilege to serve at your {{eventType}} on {{eventDate}}." +
            "          </p>" +
            "          <p style=\"font-size:15px;line-height:1.65;margin:0 0 18px;\">" +
            "            We would love to hear about your experience — even a few honest lines help us grow and serve every future event better." +
            "          </p>" +
            "          <p style=\"text-align:center;margin:32px 0 24px;\">" +
            "            <a href=\"{{reviewLink}}\" " +
            "               style=\"display:inline-block;background:#c9882f;color:#fff;text-decoration:none;padding:14px 28px;border-radius:8px;font-weight:600;font-size:15px;letter-spacing:0.02em;\">" +
            "              Share your feedback" +
            "            </a>" +
            "          </p>" +
            "          <p style=\"color:#7a7a7a;font-size:13px;margin:0 0 8px;\">" +
            "            This personal link expires on {{expiresAt}}." +
            "          </p>" +
            "          <p style=\"color:#7a7a7a;font-size:13px;margin:0;\">" +
            "            With warm regards,<br>" +
            "            The Sri Karthikeya Caterers family" +
            "          </p>" +
            "        </td></tr>" +
            "      </table>" +
            "    </td></tr>" +
            "  </table>" +
            "</body></html>";

    private static final String REVIEW_INVITATION_TEXT =
            "Dear {{clientName}},\n\n" +
            "It was our privilege to serve at your {{eventType}} on {{eventDate}}.\n\n" +
            "We would love to hear about your experience. Please share your feedback here:\n" +
            "{{reviewLink}}\n\n" +
            "This personal link expires on {{expiresAt}}.\n\n" +
            "With warm regards,\n" +
            "The Sri Karthikeya Caterers family";

    private static final String QUOTE_CONFIRMATION_HTML =
            "<!doctype html>" +
            "<html><body style=\"margin:0;padding:0;background:#fdf7ec;font-family:Inter,Arial,sans-serif;color:#1a1a1a;\">" +
            "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fdf7ec;padding:32px 0;\">" +
            "    <tr><td align=\"center\">" +
            "      <table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff;border:1px solid #ecdfc4;border-radius:12px;padding:36px 40px;\">" +
            "        <tr><td>" +
            "          <h1 style=\"font-family:Fraunces,Georgia,serif;color:#143a26;font-size:26px;font-weight:400;margin:0 0 12px;\">" +
            "            Thank you, {{clientName}}." +
            "          </h1>" +
            "          <p style=\"font-size:15px;line-height:1.65;margin:0 0 16px;\">" +
            "            We have received your enquiry for {{eventType}} on {{eventDate}}." +
            "          </p>" +
            "          <p style=\"font-size:15px;line-height:1.65;margin:0 0 24px;\">" +
            "            Our team will reach out within 24 hours with a tailored quote." +
            "          </p>" +
            "          <p style=\"color:#7a7a7a;font-size:13px;margin:0;\">" +
            "            With warm regards,<br>" +
            "            Sri Karthikeya Caterers" +
            "          </p>" +
            "        </td></tr>" +
            "      </table>" +
            "    </td></tr>" +
            "  </table>" +
            "</body></html>";

    private static final String QUOTE_CONFIRMATION_TEXT =
            "Dear {{clientName}},\n\n" +
            "We have received your enquiry for {{eventType}} on {{eventDate}}.\n\n" +
            "Our team will reach out within 24 hours with a tailored quote.\n\n" +
            "With warm regards,\nSri Karthikeya Caterers";
}
