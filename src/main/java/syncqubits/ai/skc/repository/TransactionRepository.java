package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t " +
           "WHERE " +
           "(:q = '' OR LOWER(t.reference) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(COALESCE(t.transactionRef, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:direction IS NULL OR t.direction = :direction) AND " +
           "(:status    IS NULL OR t.status    = :status) AND " +
           "(:method    IS NULL OR t.method    = :method) AND " +
           "(:category  IS NULL OR t.category  = :category) AND " +
           "(:bookingId IS NULL OR t.booking.id  = :bookingId) AND " +
           "(:invoiceId IS NULL OR t.invoice.id  = :invoiceId) AND " +
           "(:vendorId  IS NULL OR t.vendor.id   = :vendorId) AND " +
           "(:poId      IS NULL OR t.purchaseOrder.id = :poId) AND " +
           "(:clientId  IS NULL OR t.client.id   = :clientId) AND " +
           "(CAST(:fromDate AS timestamp) IS NULL OR t.paidAt >= :fromDate) AND " +
           "(CAST(:toDate   AS timestamp) IS NULL OR t.paidAt <= :toDate)")
    Page<Transaction> searchTransactions(
        @Param("q") String q,
        @Param("direction") Transaction.Direction direction,
        @Param("status")    Transaction.TxnStatus status,
        @Param("method")    Transaction.Method    method,
        @Param("category")  Transaction.ExpenseCategory category,
        @Param("bookingId") UUID bookingId,
        @Param("invoiceId") UUID invoiceId,
        @Param("vendorId")  UUID vendorId,
        @Param("poId")      UUID poId,
        @Param("clientId")  UUID clientId,
        @Param("fromDate")  Instant fromDate,
        @Param("toDate")    Instant toDate,
        Pageable pageable
    );

    @Query("SELECT t FROM Transaction t WHERE t.booking.id = :bookingId ORDER BY t.paidAt DESC")
    List<Transaction> findByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT t FROM Transaction t WHERE t.invoice.id = :invoiceId ORDER BY t.paidAt DESC")
    List<Transaction> findByInvoiceId(@Param("invoiceId") UUID invoiceId);

    /** Sum of non-refunded INCOMING for a booking — drives Booking.paidAmountCents. */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.booking.id = :bookingId " +
           "AND t.direction = syncqubits.ai.skc.entity.Transaction.Direction.INCOMING " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED")
    long sumPaidForBooking(@Param("bookingId") UUID bookingId);

    /** Sum of non-refunded INCOMING tagged to an invoice — drives Invoice.paidCents. */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.invoice.id = :invoiceId " +
           "AND t.direction = syncqubits.ai.skc.entity.Transaction.Direction.INCOMING " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED")
    long sumPaidForInvoice(@Param("invoiceId") UUID invoiceId);

    /** Sum of OUTGOING tagged to a booking but not linked to a PO — drives
     *  Booking.directExpenseCents. Excludes PO-linked spend because POs
     *  have their own paid_cents. */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.booking.id = :bookingId " +
           "AND t.direction = syncqubits.ai.skc.entity.Transaction.Direction.OUTGOING " +
           "AND t.purchaseOrder IS NULL " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED")
    long sumDirectExpenseForBooking(@Param("bookingId") UUID bookingId);

    /** Sum of OUTGOING tagged to a PO — drives PurchaseOrder.paidCents. */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.purchaseOrder.id = :poId " +
           "AND t.direction = syncqubits.ai.skc.entity.Transaction.Direction.OUTGOING " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED")
    long sumPaidForPo(@Param("poId") UUID poId);

    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.direction = :direction " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED " +
           "AND t.paidAt >= :since")
    long sumByDirectionSince(@Param("direction") Transaction.Direction direction,
                             @Param("since")     Instant since);

    @Query("SELECT t.category, COALESCE(SUM(t.amountCents), 0) FROM Transaction t " +
           "WHERE t.direction = syncqubits.ai.skc.entity.Transaction.Direction.OUTGOING " +
           "AND t.status <> syncqubits.ai.skc.entity.Transaction.TxnStatus.REFUNDED " +
           "AND t.paidAt >= :since " +
           "GROUP BY t.category")
    List<Object[]> spendByCategorySince(@Param("since") Instant since);

    long countByDirection(Transaction.Direction direction);

    @Query(value = "SELECT nextval('transaction_reference_seq')", nativeQuery = true)
    Long nextReferenceNumber();
}
