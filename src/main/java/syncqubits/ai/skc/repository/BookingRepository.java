package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByReference(String reference);

    boolean existsByQuoteRequestId(UUID quoteRequestId);

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.client " +
           "WHERE " +
           "(LOWER(b.reference) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(b.client.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(b.client.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(b.eventType) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(COALESCE(b.venueName, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:eventType = '' OR b.eventType = :eventType) AND " +
           "(CAST(:fromDate AS date) IS NULL OR b.eventDate >= :fromDate) AND " +
           "(CAST(:toDate   AS date) IS NULL OR b.eventDate <= :toDate)")
    Page<Booking> searchBookings(
        @Param("q") String q,
        @Param("status") Booking.BookingStatus status,
        @Param("eventType") String eventType,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate")   LocalDate toDate,
        Pageable pageable
    );

    @Query("SELECT b FROM Booking b WHERE b.client.id = :clientId ORDER BY b.eventDate DESC")
    List<Booking> findByClientIdOrderByEventDateDesc(@Param("clientId") UUID clientId);

    long countByStatus(Booking.BookingStatus status);

    @Query("SELECT b.client.id, COUNT(b) FROM Booking b WHERE b.client.id IN :ids GROUP BY b.client.id")
    List<Object[]> countByClientIds(@Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT b.client.id, COALESCE(SUM(b.paidAmountCents), 0) FROM Booking b " +
           "WHERE b.client.id IN :ids GROUP BY b.client.id")
    List<Object[]> sumPaidByClientIds(@Param("ids") java.util.Collection<UUID> ids);

    /** Reserve the next reference number from {@code booking_reference_seq}. */
    @Query(value = "SELECT nextval('booking_reference_seq')", nativeQuery = true)
    Long nextReferenceNumber();

    @Query("SELECT b FROM Booking b WHERE b.eventDate >= :from AND b.eventDate <= :to ORDER BY b.eventDate ASC")
    List<Booking> findUpcoming(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
