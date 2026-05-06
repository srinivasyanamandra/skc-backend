package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.EmailTemplate;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    Optional<EmailTemplate> findByName(String name);

    boolean existsByName(String name);

    /**
     * Code-based transactional lookup. Used by services that resolve a
     * template by stable code (e.g. {@code SUBSCRIBE}) rather than by id —
     * lets admins author/override copy through the admin UI without redeploys.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT t FROM EmailTemplate t " +
        "WHERE LOWER(t.code) = LOWER(:code) AND t.isActive = true")
    Optional<EmailTemplate> findByCodeIgnoreCaseAndIsActiveTrue(
        @org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Query(
        "SELECT (COUNT(t) > 0) FROM EmailTemplate t WHERE LOWER(t.code) = LOWER(:code)")
    boolean existsByCodeIgnoreCase(
        @org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Query(
        "SELECT (COUNT(t) > 0) FROM EmailTemplate t " +
        "WHERE LOWER(t.code) = LOWER(:code) AND t.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(
        @org.springframework.data.repository.query.Param("code") String code,
        @org.springframework.data.repository.query.Param("id") UUID id);

    Page<EmailTemplate> findByType(EmailTemplate.TemplateType type, Pageable pageable);

    Page<EmailTemplate> findByIsActive(Boolean isActive, Pageable pageable);

    Optional<EmailTemplate> findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(EmailTemplate.TemplateType type);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t FROM EmailTemplate t WHERE " +
        "(LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
        " OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :q, '%')) " +
        " OR LOWER(COALESCE(t.code, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
        "(:type IS NULL OR t.type = :type) AND " +
        "(:isActive IS NULL OR t.isActive = :isActive)")
    Page<EmailTemplate> adminSearch(
        @org.springframework.data.repository.query.Param("q") String q,
        @org.springframework.data.repository.query.Param("type") EmailTemplate.TemplateType type,
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        Pageable pageable
    );
}
