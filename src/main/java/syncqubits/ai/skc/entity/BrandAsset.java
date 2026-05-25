package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a single uploaded brand asset (logo, signature, QR, etc.).
 * The binary file lives on disk under {@code studio.assets.dir} — this row
 * captures the filename, original upload name, MIME type, size, free-form
 * role tag, and the public URL the file is served from.
 *
 * <p>Role is intentionally a plain {@code String} (not an enum) so admins
 * can introduce new categories (seasonal banner, festival motif…) without
 * a schema migration. The frontend constrains the picker to a known set
 * via {@code AssetRoles}; the backend stays permissive.
 */
@Entity
@Table(name = "brand_assets", indexes = {
    @Index(name = "idx_brand_assets_role",    columnList = "role"),
    @Index(name = "idx_brand_assets_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Server-assigned safe filename on disk (UUID-prefixed). */
    @Column(nullable = false, length = 200)
    private String filename;

    /** Original filename from the upload, preserved for the gallery UI. */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String role = "DECORATIVE";

    @Column(name = "alt_text", length = 255)
    private String altText;

    /** Fully-qualified public path (e.g. {@code /uploads/<filename>}). */
    @Column(name = "public_url", nullable = false, length = 500)
    private String publicUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
