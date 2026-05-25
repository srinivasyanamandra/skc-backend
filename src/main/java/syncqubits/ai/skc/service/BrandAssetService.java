package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import syncqubits.ai.skc.config.AppProperties;
import syncqubits.ai.skc.dto.asset.BrandAssetResponse;
import syncqubits.ai.skc.entity.BrandAsset;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BrandAssetRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Storage + lifecycle for Document Studio brand assets.
 *
 * <p>Files are persisted on local disk under {@code studio.assets.dir}.
 * Filenames are server-assigned ({@code UUID + sanitized-extension}) so a
 * collision is impossible and traversal payloads in the upload's original
 * name can't reach the filesystem. The original name is kept in the DB
 * for display only.
 *
 * <p>Allowed MIME types are restricted to common web image formats — the
 * Document Studio doesn't render anything else, and a tight whitelist
 * stops PDFs / executables / SVGs-with-script from landing in the upload
 * dir. SVG is allowed because logo files often arrive in that format;
 * Spring's static handler serves them with the correct content type but
 * never executes them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandAssetService {

    private static final Set<String> ALLOWED_MIME = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp", "image/svg+xml"
    );

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    private final BrandAssetRepository repository;
    private final AppProperties appProperties;

    public List<BrandAssetResponse> list(String role) {
        List<BrandAsset> rows = (role == null || role.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByRoleOrderByCreatedAtDesc(role.trim().toUpperCase());
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public BrandAssetResponse upload(MultipartFile file, String role, String altText) {
        validate(file);

        String safeRole = normalizeRole(role);
        String safeAlt  = (altText == null || altText.isBlank()) ? null : altText.trim();
        String ext      = extensionFor(file.getContentType(), file.getOriginalFilename());
        String filename = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path dir = Paths.get(appProperties.getStudio().getAssets().getDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist uploaded asset: " + e.getMessage(), e);
        }

        String publicBase = appProperties.getStudio().getAssets().getPublicBase();
        String publicUrl  = (publicBase.endsWith("/") ? publicBase : publicBase + "/") + filename;

        BrandAsset saved = repository.save(BrandAsset.builder()
                .filename(filename)
                .originalName(safeOriginalName(file.getOriginalFilename()))
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .role(safeRole)
                .altText(safeAlt)
                .publicUrl(publicUrl)
                .build());

        log.info("BrandAsset uploaded id={} role={} size={}B name={}",
                 saved.getId(), saved.getRole(), saved.getSizeBytes(), saved.getOriginalName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        BrandAsset asset = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset " + id + " not found"));

        Path dir = Paths.get(appProperties.getStudio().getAssets().getDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(asset.getFilename()).normalize();

        /* Defense-in-depth: refuse to delete anything outside the configured
           dir. The filename comes from our own writer above so this should
           never trigger — but if a malicious row got inserted directly via
           SQL, this stops us from rm-ing a system file. */
        if (!target.startsWith(dir)) {
            log.warn("Refusing to delete asset {} — resolved path {} escapes {}", id, target, dir);
        } else {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("Could not remove file {} for asset {}: {}", target, id, e.getMessage());
            }
        }

        repository.delete(asset);
        log.info("BrandAsset deleted id={}", id);
    }

    /* ─────────────────────────── helpers ──────────────────────────────── */

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File exceeds 5MB limit");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_MIME.contains(ct.toLowerCase())) {
            throw new BadRequestException(
                "Unsupported file type" + (ct == null ? "" : " (" + ct + ")") +
                ". Allowed: PNG, JPG, WEBP, SVG."
            );
        }
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "DECORATIVE";
        String r = role.trim().toUpperCase();
        /* Permissive: any letter/digit/underscore role is accepted so admins
           can introduce new ones (SEASONAL_*, FESTIVAL_*) without a schema
           change. Reject anything with whitespace or punctuation. */
        if (!r.matches("[A-Z0-9_]{1,40}")) {
            throw new BadRequestException("Invalid role; use letters/digits/underscore, max 40 chars");
        }
        return r;
    }

    private static String extensionFor(String contentType, String originalName) {
        if (contentType != null) {
            return switch (contentType.toLowerCase()) {
                case "image/png"     -> "png";
                case "image/jpeg",
                     "image/jpg"     -> "jpg";
                case "image/webp"    -> "webp";
                case "image/svg+xml" -> "svg";
                default -> "";
            };
        }
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String ext = originalName.substring(dot + 1).toLowerCase();
                if (ext.matches("[a-z0-9]{1,5}")) return ext;
            }
        }
        return "";
    }

    /** Strip path separators so the display name never resembles a path. */
    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "asset";
        String trimmed = name.replaceAll("[\\\\/]", "_").trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private BrandAssetResponse toResponse(BrandAsset a) {
        return BrandAssetResponse.builder()
                .id(a.getId())
                .filename(a.getFilename())
                .originalName(a.getOriginalName())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .role(a.getRole())
                .altText(a.getAltText())
                .publicUrl(a.getPublicUrl())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
