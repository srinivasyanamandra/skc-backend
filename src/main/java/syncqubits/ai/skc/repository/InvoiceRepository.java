package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i FROM Invoice i " +
           "JOIN FETCH i.client " +
           "JOIN FETCH i.booking " +
           "WHERE " +
           "(:q = '' OR LOWER(i.reference) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(i.client.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(i.booking.reference) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR i.status = :status) AND " +
           "(:bookingId IS NULL OR i.booking.id = :bookingId) AND " +
           "(:clientId  IS NULL OR i.client.id  = :clientId) AND " +
           "(CAST(:fromDate AS date) IS NULL OR i.issueDate >= :fromDate) AND " +
           "(CAST(:toDate   AS date) IS NULL OR i.issueDate <= :toDate)")
    Page<Invoice> searchInvoices(
        @Param("q") String q,
        @Param("status")    Invoice.InvoiceStatus status,
        @Param("bookingId") UUID bookingId,
        @Param("clientId")  UUID clientId,
        @Param("fromDate")  LocalDate fromDate,
        @Param("toDate")    LocalDate toDate,
        Pageable pageable
    );

    @Query("SELECT i FROM Invoice i WHERE i.booking.id = :bookingId ORDER BY i.createdAt DESC")
    List<Invoice> findByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT i FROM Invoice i WHERE i.client.id = :clientId ORDER BY i.createdAt DESC")
    List<Invoice> findByClientId(@Param("clientId") UUID clientId);

    long countByStatus(Invoice.InvoiceStatus status);

    /** Outstanding receivables = sum of (total - paid) for non-terminal
     *  invoices (DRAFT and VOID don't owe us money). */
    @Query("SELECT COALESCE(SUM(i.totalCents - i.paidCents), 0) FROM Invoice i " +
           "WHERE i.status IN (syncqubits.ai.skc.entity.Invoice.InvoiceStatus.ISSUED, " +
                              "syncqubits.ai.skc.entity.Invoice.InvoiceStatus.PARTIALLY_PAID, " +
                              "syncqubits.ai.skc.entity.Invoice.InvoiceStatus.OVERDUE)")
    long sumOutstandingCents();

    /** Total invoiced (non-VOID) for a booking — drives Booking.invoicedAmountCents. */
    @Query("SELECT COALESCE(SUM(i.totalCents), 0) FROM Invoice i " +
           "WHERE i.booking.id = :bookingId AND i.status <> syncqubits.ai.skc.entity.Invoice.InvoiceStatus.VOID")
    long sumInvoicedForBooking(@Param("bookingId") UUID bookingId);

    @Query(value = "SELECT nextval('invoice_reference_seq')", nativeQuery = true)
    Long nextReferenceNumber();

    /** Auto-OVERDUE sweep: invoices past due_date that still owe money. */
    @Query("SELECT i FROM Invoice i WHERE i.dueDate < :today " +
           "AND i.status IN (syncqubits.ai.skc.entity.Invoice.InvoiceStatus.ISSUED, " +
                            "syncqubits.ai.skc.entity.Invoice.InvoiceStatus.PARTIALLY_PAID)")
    List<Invoice> findCandidatesForOverdueSweep(@Param("today") LocalDate today);
}
