package syncqubits.ai.skc.service.email;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for the placeholder set every template can rely
 * on. Mirrors the EmailBuilder UI's {@code VARIABLES} catalogue
 * (clientName, firstName, eventType, eventDate, guests, reviewLink,
 * quoteLink, brand, year) plus the legacy {@code name}/{@code email}
 * aliases that older templates may still reference.
 *
 * Two factories:
 *   • {@link #sampleSet} — populated with safe placeholder values for the
 *     "test send" path so a freshly authored template renders cleanly even
 *     before any real client data exists.
 *   • {@link #liveSet}   — empty defaults so the caller can layer real
 *     values in. Use this for production sends; never let {@code null}
 *     leak into a {@code vars.put}.
 *
 * Add an entry here when introducing a new placeholder so every send path
 * picks it up automatically.
 */
public final class TemplateVariables {

    private TemplateVariables() {}

    private static final DateTimeFormatter HUMAN_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Test-send defaults — every key has a non-null, render-ready value. */
    public static Map<String, Object> sampleSet(String displayName, String firstName, String email) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("clientName",   nz(displayName, "Friend"));
        v.put("name",         nz(displayName, "Friend"));
        v.put("firstName",    nz(firstName,   "Friend"));
        v.put("email",        nz(email,       ""));
        v.put("eventType",    "Wedding");
        v.put("eventDate",    LocalDate.now().plusDays(30).format(HUMAN_DATE));
        v.put("guests",       "250");
        v.put("guestCount",   "250");          // alias for legacy templates
        v.put("reviewLink",   "https://example.invalid/test/review");
        v.put("quoteLink",    "https://example.invalid/test/quote");
        v.put("brand",        BrandedEmailLayout.BRAND_NAME);
        v.put("year",         String.valueOf(Year.now().getValue()));
        v.put("month",        LocalDate.now().toString());
        v.put("unsubscribeUrl", "#");           // never resolvable in test
        return v;
    }

    /**
     * Production-send seed: pre-populates {@code brand} and {@code year}
     * (always identical) and leaves the rest blank so the caller fills in
     * the real recipient/event/link values without forgetting any.
     */
    public static Map<String, Object> liveSet() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("clientName",   "");
        v.put("name",         "");
        v.put("firstName",    "");
        v.put("email",        "");
        v.put("eventType",    "");
        v.put("eventDate",    "");
        v.put("guests",       "");
        v.put("guestCount",   "");
        v.put("reviewLink",   "");
        v.put("quoteLink",    "");
        v.put("brand",        BrandedEmailLayout.BRAND_NAME);
        v.put("year",         String.valueOf(Year.now().getValue()));
        return v;
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
