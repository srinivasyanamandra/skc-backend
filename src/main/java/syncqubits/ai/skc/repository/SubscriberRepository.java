package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Subscriber;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, UUID> {

    Optional<Subscriber> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT s FROM Subscriber s WHERE " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:active IS NULL OR s.isActive = :active) AND " +
           "(CAST(:since AS timestamp) IS NULL OR s.createdAt >= :since) AND " +
           "(CAST(:until AS timestamp) IS NULL OR s.createdAt <= :until)")
    Page<Subscriber> searchSubscribers(
        @Param("q") String query,
        @Param("active") Boolean active,
        @Param("since") Instant since,
        @Param("until") Instant until,
        Pageable pageable
    );

    long countByIsActive(Boolean isActive);

    @Query("SELECT s FROM Subscriber s WHERE " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:active IS NULL OR s.isActive = :active) AND " +
           "(CAST(:since AS timestamp) IS NULL OR s.createdAt >= :since) AND " +
           "(CAST(:until AS timestamp) IS NULL OR s.createdAt <= :until)")
    java.util.List<Subscriber> resolveSubscribers(
        @Param("q") String q,
        @Param("active") Boolean active,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    @Query("SELECT s FROM Subscriber s WHERE s.id IN :ids")
    java.util.List<Subscriber> findAllByIds(@Param("ids") java.util.Collection<UUID> ids);
}
