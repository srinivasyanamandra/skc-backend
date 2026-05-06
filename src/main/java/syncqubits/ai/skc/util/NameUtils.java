package syncqubits.ai.skc.util;

import java.util.Arrays;
import java.util.stream.Collectors;



/**
 * Derives a clean, human-readable display name for email personalisation.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Use the stored name if it is non-blank (always preferred).
 *   <li>Derive from the local-part of the email address.
 *   <li>Fall back to {@code "Valued Customer"} if derivation yields nothing usable.
 * </ol>
 *
 * <p>Derivation rules applied to the email local-part:
 * <ul>
 *   <li>Strip all digits and characters that are not letters, dots, underscores, or hyphens.
 *   <li>Split on {@code [._-]+} to get word tokens.
 *   <li>Discard tokens shorter than 2 characters.
 *   <li>Title-case each remaining token.
 *   <li>Join with a single space.
 *   <li>If the result is still blank or shorter than 2 characters, use the fallback.
 * </ul>
 *
 * <p>Examples:
 * <pre>
 *   resolveDisplayName("Srinivas Yanamandra", "any@email.com") → "Srinivas Yanamandra"
 *   resolveDisplayName(null,  "john.doe123@example.com")       → "John Doe"
 *   resolveDisplayName("",    "srinivas_1989@gmail.com")       → "Srinivas"
 *   resolveDisplayName(null,  "info@srikarthikeyacaterers.in") → "Info"
 *   resolveDisplayName(null,  "123@example.com")               → "Valued Customer"
 *   resolveDisplayName(null,  "a@example.com")                 → "Valued Customer"
 *   resolveDisplayName("John", "john.doe@example.com")         → "John"
 * </pre>
 *
 * <p><strong>Variable Usage Guidelines:</strong>
 * <ul>
 *   <li>Use {@code {{clientName}}} or {@code {{name}}} for full display name</li>
 *   <li>Use {@code {{firstName}}} for informal greetings</li>
 *   <li>Use {@code {{eventType}}}, {@code {{eventDate}}}, {@code {{guestCount}}} only when contextually relevant</li>
 *   <li>Use {@code {{reviewLink}}} only in review/feedback emails</li>
 * </ul>
 */
public final class NameUtils {

    private static final String FALLBACK = "Valued Customer";

    private NameUtils() {}

    /**
     * Returns the best available display name for a recipient.
     *
     * @param storedName the name stored in the database (may be null or blank)
     * @param email      the recipient's email address (may be null)
     * @return a non-null, non-blank display name
     */
    public static String resolveDisplayName(String storedName, String email) {
        if (storedName != null && !storedName.isBlank()) {
            return storedName.trim();
        }
        return deriveFromEmail(email);
    }

    /**
     * Returns only the first word of the display name — suitable for
     * informal greetings like "Dear {{firstName}}".
     */
    public static String resolveFirstName(String storedName, String email) {
        String full = resolveDisplayName(storedName, email);
        if (full.equals(FALLBACK)) return FALLBACK;
        String first = full.split("\\s+")[0];
        return first.isBlank() ? FALLBACK : first;
    }

    // ---------------------------------------------------------------- private

    static String deriveFromEmail(String email) {
        if (email == null || email.isBlank()) return FALLBACK;

        // Extract local-part (before @)
        int atIdx = email.indexOf('@');
        String local = atIdx > 0 ? email.substring(0, atIdx) : email;

        // Remove digits and characters that are not letters, dots, underscores, hyphens
        String cleaned = local.replaceAll("[^a-zA-Z._-]", "");

        // Split on separator characters
        String[] tokens = cleaned.split("[._-]+");

        // Title-case each token, discard tokens shorter than 2 chars
        String result = Arrays.stream(tokens)
                .filter(t -> t.length() >= 2)
                .map(NameUtils::titleCase)
                .collect(Collectors.joining(" "));

        return result.length() >= 2 ? result : FALLBACK;
    }

    private static String titleCase(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }
}
