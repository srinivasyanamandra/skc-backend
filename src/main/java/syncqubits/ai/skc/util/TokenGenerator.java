package syncqubits.ai.skc.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGenerator {

    private static final SecureRandom RNG = new SecureRandom();

    private TokenGenerator() {}

    /**
     * 32 bytes of entropy → 43-char URL-safe base64 string.
     * Comfortably fits the 64-char column on reviews.token.
     */
    public static String urlSafe32() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
