package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.SystemLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, UUID> {

    Page<SystemLog> findByType(String type, Pageable pageable);

    Page<SystemLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    @Query("SELECT l FROM SystemLog l WHERE l.entityType = :entityType AND l.entityId = :entityId ORDER BY l.createdAt DESC")
    List<SystemLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        @Param("entityType") String entityType,
        @Param("entityId") UUID entityId
    );

    @Query("SELECT l FROM SystemLog l ORDER BY l.createdAt DESC")
    Page<SystemLog> findRecentActivity(Pageable pageable);
}
