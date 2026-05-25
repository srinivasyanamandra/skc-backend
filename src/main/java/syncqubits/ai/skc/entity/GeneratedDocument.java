package syncqubits.ai.skc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Log row for a rendered Document Studio PDF.
 *
 * <p>Decoupled from {@code Booking}/{@code Client} by holding their IDs as
 * plain UUIDs (no FK) so this table can record renders even for ad-hoc
 * documents that aren't tied to a specific operational entity. When a
 * booking/client *is* known, the FK columns get populated for later
 * grouping in the Print Center.
 */
@Entity
@Table(name = "generated_documents", indexes = {
    @Index(name = "idx_generated_documents_type",    columnList = "document_type"),
    @Index(name = "idx_generated_documents_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Discriminator: letterhead, menu, invoice, proposal, po, welcome_note. */
    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    /** Human-readable id when one exists (INV-2026-001 …). Null for ad-hoc. */
    @Column(name = "document_number", length = 60)
    private String documentNumber;

    /** Server-assigned filename on disk under {studio.assets.dir}/documents. */
    @Column(name = "pdf_filename", nullable = false, length = 200)
    private String pdfFilename;

    /** Public URL the PDF is served from (e.g. /uploads/documents/<file>.pdf). */
    @Column(name = "pdf_url", nullable = false, length = 500)
    private String pdfUrl;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** Template version locked at render time. Future template redesigns
     *  shouldn't change how an already-rendered document looks when the
     *  admin re-downloads it. */
    @Column(name = "template_version", nullable = false, length = 40)
    @Builder.Default
    private String templateVersion = "v1";

    @Column(name = "rendered_by_email", length = 255)
    private String renderedByEmail;

    @Column(name = "branding_id")
    private UUID brandingId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "client_id")
    private UUID clientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
