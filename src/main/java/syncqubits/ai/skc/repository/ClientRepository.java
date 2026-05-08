package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Client;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Client c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(CAST(:since AS timestamp) IS NULL OR c.createdAt >= :since) AND " +
           "(CAST(:until AS timestamp) IS NULL OR c.createdAt <= :until)")
    Page<Client> searchClients(
        @Param("q") String query,
        @Param("status") Client.ClientStatus status,
        @Param("since") Instant since,
        @Param("until") Instant until,
        Pageable pageable
    );

    long countByStatus(Client.ClientStatus status);

    @Query("SELECT c FROM Client c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(CAST(:since AS timestamp) IS NULL OR c.createdAt >= :since) AND " +
           "(CAST(:until AS timestamp) IS NULL OR c.createdAt <= :until) AND " +
           "(:eventType = '' OR EXISTS (SELECT 1 FROM QuoteRequest q WHERE q.client = c AND q.eventType = :eventType) " +
           "                OR EXISTS (SELECT 1 FROM Review r       WHERE r.client = c AND r.eventType = :eventType)) AND " +
           "(:minRating IS NULL OR EXISTS (SELECT 1 FROM Review r WHERE r.client = c AND r.type = syncqubits.ai.skc.entity.Review.ReviewType.REVIEW " +
           "                                                       AND r.status = syncqubits.ai.skc.entity.Review.ReviewStatus.APPROVED " +
           "                                                       AND r.overallRating >= :minRating))")
    java.util.List<Client> resolveClients(
        @Param("q") String q,
        @Param("status") Client.ClientStatus status,
        @Param("since") Instant since,
        @Param("until") Instant until,
        @Param("eventType") String eventType,
        @Param("minRating") Short minRating
    );

    @Query("SELECT c FROM Client c WHERE c.id IN :ids")
    java.util.List<Client> findAllByIds(@Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT MAX(q.eventDate) FROM QuoteRequest q WHERE q.client.id = :clientId")
    java.time.LocalDate latestQuoteEventDate(@Param("clientId") UUID clientId);

    @Query("SELECT MAX(r.eventDate) FROM Review r WHERE r.client.id = :clientId")
    java.time.LocalDate latestReviewEventDate(@Param("clientId") UUID clientId);

    /** Distinct tags currently in use across all clients. Native query
     *  unnests the {@code tags TEXT[]} column; ordering is alphabetical. */
    @Query(value = "SELECT DISTINCT t FROM clients c, unnest(c.tags) AS t " +
                   "WHERE c.tags IS NOT NULL AND array_length(c.tags, 1) > 0 ORDER BY t",
           nativeQuery = true)
    java.util.List<String> distinctTags();
}
