package syncqubits.ai.skc.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single-process Playwright wrapper that turns arbitrary HTML into a PDF
 * byte stream. One Chromium browser is launched lazily on first request
 * and reused across the JVM's lifetime — boot is the slowest part of a
 * cold render (~1-2s) so we pay it once.
 *
 * <p>Each render creates a short-lived {@link com.microsoft.playwright.BrowserContext}
 * + page, so concurrent requests get isolated cookies / caches.
 *
 * <p>Local-dev prerequisite: run {@code mvnw exec:java
 * -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install chromium"}
 * once per workstation. Production Dockerfile handles this at build time.
 */
@Service
@Slf4j
public class PdfRenderService {

    private volatile Playwright playwright;
    private volatile Browser    browser;
    private final Object initLock = new Object();

    /**
     * Render the given HTML string to a PDF byte array.
     *
     * @param html       full HTML document (DOCTYPE → /html)
     * @param baseUrl    absolute origin used to resolve relative URLs
     *                   inside the HTML (e.g. images at {@code /uploads/…})
     * @return PDF bytes (A4)
     */
    public byte[] renderToPdf(String html, String baseUrl) {
        Browser b = ensureBrowser();
        try (var context = b.newContext();
             var page = context.newPage()) {

            /* baseURL on the context lets us call page.setContent() with
               relative URLs and have the browser resolve them. */
            if (baseUrl != null && !baseUrl.isBlank()) {
                // setContent ignores context.baseURL; we serve via setContent
                // and rely on absolute URLs in the HTML instead.
                log.debug("Render baseUrl: {}", baseUrl);
            }

            page.setContent(html, new Page.SetContentOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)
                    .setTimeout(15_000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            return page.pdf(new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setMargin(new com.microsoft.playwright.options.Margin()
                            .setTop("0").setBottom("0").setLeft("0").setRight("0")));
        }
    }

    /* ─────────────────────────── lifecycle ─────────────────────────────── */

    private Browser ensureBrowser() {
        Browser current = browser;
        if (current != null) return current;
        synchronized (initLock) {
            if (browser != null) return browser;
            try {
                playwright = Playwright.create();
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true));
                log.info("Playwright Chromium launched for PDF rendering");
                return browser;
            } catch (Exception e) {
                /* Most common cause is "Playwright browsers aren't installed
                   on this host". Use ResponseStatusException so the global
                   exception handler preserves the helpful message in the
                   API envelope (the generic Exception handler swallows
                   messages for security). */
                String msg = "PDF renderer is not ready. "
                        + "Run: mvnw exec:java -Dexec.mainClass=\"com.microsoft.playwright.CLI\" "
                        + "-Dexec.args=\"install chromium\" — and restart the backend. "
                        + "Underlying error: " + e.getMessage();
                log.error(msg, e);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg, e);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            if (browser != null) {
                browser.close();
                log.info("Playwright browser closed");
            }
        } catch (Exception e) {
            log.warn("Failed to close Playwright browser: {}", e.getMessage());
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close Playwright instance: {}", e.getMessage());
        }
    }
}
