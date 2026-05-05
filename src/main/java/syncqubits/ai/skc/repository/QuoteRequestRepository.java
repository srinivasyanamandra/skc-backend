package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.QuoteRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, UUID> {

    @Query("SELECT q FROM QuoteRequest q " +
           "JOIN FETCH q.client " +
           "WHERE " +
           "(:status IS NULL OR q.status = :status) AND " +
           "(:eventType = '' OR q.eventType = :eventType) AND " +
           "(CAST(:fromDate AS date) IS NULL OR q.eventDate >= :fromDate) AND " +
           "(CAST(:toDate   AS date) IS NULL OR q.eventDate <= :toDate)")
    Page<QuoteRequest> searchQuotes(
        @Param("status") QuoteRequest.QuoteStatus status,
        @Param("eventType") String eventType,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        Pageable pageable
    );

    long countByStatus(QuoteRequest.QuoteStatus status);

    @Query("SELECT COUNT(q) FROM QuoteRequest q WHERE q.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    @Query("SELECT q FROM QuoteRequest q WHERE q.client.id = :clientId ORDER BY q.createdAt DESC")
    java.util.List<QuoteRequest> findByClientIdOrderByCreatedAtDesc(@Param("clientId") UUID clientId);

    long countByClientId(UUID clientId);

    @Query("SELECT q.client.id, COUNT(q) FROM QuoteRequest q WHERE q.client.id IN :ids GROUP BY q.client.id")
    java.util.List<Object[]> countByClientIds(@Param("ids") java.util.Collection<UUID> ids);
}
