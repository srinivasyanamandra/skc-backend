package syncqubits.ai.skc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.document.GeneratedDocumentResponse;
import syncqubits.ai.skc.service.DocumentStudioService;

import java.util.List;

/**
 * Document Studio render endpoints.
 *
 * <p>Letterhead is the only document type that lands in Phase 1; future
 * slices (menus, proposals, invoices, POs) get a similarly-shaped endpoint
 * here, each backed by its own HTML template builder.
 *
 * <p>The endpoint returns the raw PDF bytes as {@code application/pdf} so
 * the browser shows them inline (and the frontend can offer a "download"
 * action). The render is also persisted under
 * {studio.assets.dir}/documents/ so the Print Center can re-serve it
 * without re-rendering.
 */
@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@Slf4j
public class AdminDocumentController {

    private final DocumentStudioService documentStudioService;

    @GetMapping(value = "/letterhead.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> renderLetterhead(HttpServletRequest request) {
        DocumentStudioService.RenderResult result =
                documentStudioService.renderLetterhead(originOf(request), currentAdminEmail());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.bytes());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<GeneratedDocumentResponse>> recent(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(documentStudioService.recent(limit, type));
    }

    /* ─────────────────────────── internals ─────────────────────────────── */

    /** Build absolute origin (scheme://host[:port]) from the inbound
     *  request so Playwright can resolve relative {@code /uploads/…} URLs
     *  during the PDF render. Honors X-Forwarded-* via Spring's
     *  forward-headers-strategy: framework. */
    private static String originOf(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host   = request.getServerName();
        int    port   = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                           || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String currentAdminEmail() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            return a == null ? null : a.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
