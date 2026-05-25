package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.config.AppProperties;
import syncqubits.ai.skc.dto.document.GeneratedDocumentResponse;
import syncqubits.ai.skc.entity.BrandingProfile;
import syncqubits.ai.skc.entity.GeneratedDocument;
import syncqubits.ai.skc.repository.BrandingProfileRepository;
import syncqubits.ai.skc.repository.GeneratedDocumentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Top-level Document Studio orchestration.
 *
 * <p>Ties together the branding profile, the HTML template builder, and
 * the Playwright PDF renderer. Persists every render under
 * {studio.assets.dir}/documents/ and logs a row in
 * {@code generated_documents} so the Print Center can list and re-download
 * without re-rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStudioService {

    private static final String DOCS_SUBDIR = "documents";

    private final LetterheadHtmlBuilder letterheadBuilder;
    private final PdfRenderService pdfRenderer;
    private final BrandingProfileRepository brandingRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final AppProperties appProperties;

    /**
     * Render the canonical letterhead to a PDF byte stream and log the
     * render in {@code generated_documents}.
     *
     * @param publicBase absolute server origin (e.g. {@code http://localhost:8080})
     * @param actorEmail email of the admin triggering the render (for audit)
     */
    @Transactional
    public RenderResult renderLetterhead(String publicBase, String actorEmail) {
        BrandingProfile profile = brandingRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("No branding profile configured"));

        String html = letterheadBuilder.build(profile, publicBase, null);
        byte[] pdf  = pdfRenderer.renderToPdf(html, publicBase);

        String filename = "letterhead-" + UUID.randomUUID() + ".pdf";
        Path written = writeToDisk(filename, pdf);

        String publicUrl = (appProperties.getStudio().getAssets().getPublicBase()
                .replaceAll("/+$", "")) + "/" + DOCS_SUBDIR + "/" + filename;

        GeneratedDocument row = documentRepository.save(GeneratedDocument.builder()
                .documentType("letterhead")
                .pdfFilename(filename)
                .pdfUrl(publicUrl)
                .sizeBytes((long) pdf.length)
                .templateVersion(LetterheadHtmlBuilder.VERSION)
                .renderedByEmail(actorEmail)
                .brandingId(profile.getId())
                .build());

        log.info("Letterhead rendered id={} bytes={} path={}", row.getId(), pdf.length, written);
        return new RenderResult(row.getId(), filename, pdf);
    }

    public List<GeneratedDocumentResponse> recent(int limit, String type) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var pageReq = PageRequest.of(0, safeLimit);
        Page<GeneratedDocument> page = (type == null || type.isBlank())
                ? documentRepository.findAllByOrderByCreatedAtDesc(pageReq)
                : documentRepository.findByDocumentTypeOrderByCreatedAtDesc(type.trim().toLowerCase(), pageReq);
        return page.getContent().stream().map(this::toResponse).toList();
    }

    /* ─────────────────────────── internals ─────────────────────────────── */

    private Path writeToDisk(String filename, byte[] bytes) {
        Path dir = Paths.get(appProperties.getStudio().getAssets().getDir(), DOCS_SUBDIR)
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist rendered PDF: " + e.getMessage(), e);
        }
    }

    private GeneratedDocumentResponse toResponse(GeneratedDocument d) {
        return GeneratedDocumentResponse.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .documentNumber(d.getDocumentNumber())
                .pdfFilename(d.getPdfFilename())
                .pdfUrl(d.getPdfUrl())
                .sizeBytes(d.getSizeBytes())
                .templateVersion(d.getTemplateVersion())
                .renderedByEmail(d.getRenderedByEmail())
                .createdAt(d.getCreatedAt())
                .build();
    }

    /** Carrier for the rendered PDF + its audit-log id. */
    public record RenderResult(UUID id, String filename, byte[] bytes) {}
}
