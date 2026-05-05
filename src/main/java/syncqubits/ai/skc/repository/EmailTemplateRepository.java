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

    Page<EmailTemplate> findByType(EmailTemplate.TemplateType type, Pageable pageable);

    Page<EmailTemplate> findByIsActive(Boolean isActive, Pageable pageable);

    Optional<EmailTemplate> findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(EmailTemplate.TemplateType type);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t FROM EmailTemplate t WHERE " +
        "(LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
        " OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
        "(:type IS NULL OR t.type = :type) AND " +
        "(:isActive IS NULL OR t.isActive = :isActive)")
    Page<EmailTemplate> adminSearch(
        @org.springframework.data.repository.query.Param("q") String q,
        @org.springframework.data.repository.query.Param("type") EmailTemplate.TemplateType type,
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        Pageable pageable
    );
}
