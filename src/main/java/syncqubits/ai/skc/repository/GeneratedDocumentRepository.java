package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.GeneratedDocument;

import java.util.UUID;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    Page<GeneratedDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<GeneratedDocument> findByDocumentTypeOrderByCreatedAtDesc(String documentType, Pageable pageable);
}
