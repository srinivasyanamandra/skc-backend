package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.PurchaseOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query("SELECT p FROM PurchaseOrder p " +
           "JOIN FETCH p.vendor " +
           "WHERE " +
           "(:q = '' OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(p.vendor.name) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:vendorId  IS NULL OR p.vendor.id  = :vendorId) AND " +
           "(:bookingId IS NULL OR p.booking.id = :bookingId) AND " +
           "(CAST(:fromDate AS date) IS NULL OR p.issueDate >= :fromDate) AND " +
           "(CAST(:toDate   AS date) IS NULL OR p.issueDate <= :toDate)")
    Page<PurchaseOrder> searchPurchaseOrders(
        @Param("q")         String q,
        @Param("status")    PurchaseOrder.PoStatus status,
        @Param("vendorId")  UUID vendorId,
        @Param("bookingId") UUID bookingId,
        @Param("fromDate")  LocalDate fromDate,
        @Param("toDate")    LocalDate toDate,
        Pageable pageable
    );

    @Query("SELECT p FROM PurchaseOrder p WHERE p.vendor.id = :vendorId ORDER BY p.createdAt DESC")
    List<PurchaseOrder> findByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT p FROM PurchaseOrder p WHERE p.booking.id = :bookingId ORDER BY p.createdAt DESC")
    List<PurchaseOrder> findByBookingId(@Param("bookingId") UUID bookingId);

    long countByStatus(PurchaseOrder.PoStatus status);

    @Query("SELECT COALESCE(SUM(p.totalCents), 0) FROM PurchaseOrder p " +
           "WHERE p.status NOT IN (syncqubits.ai.skc.entity.PurchaseOrder.PoStatus.CANCELLED, syncqubits.ai.skc.entity.PurchaseOrder.PoStatus.PAID)")
    long sumOutstandingCents();

    @Query("SELECT COALESCE(SUM(p.totalCents - p.paidCents), 0) FROM PurchaseOrder p " +
           "WHERE p.vendor.id = :vendorId " +
           "AND p.status NOT IN (syncqubits.ai.skc.entity.PurchaseOrder.PoStatus.CANCELLED, syncqubits.ai.skc.entity.PurchaseOrder.PoStatus.PAID)")
    long outstandingForVendor(@Param("vendorId") UUID vendorId);

    @Query("SELECT p.vendor.id, COUNT(p), COALESCE(SUM(p.totalCents), 0) " +
           "FROM PurchaseOrder p WHERE p.vendor.id IN :ids GROUP BY p.vendor.id")
    List<Object[]> aggregateByVendorIds(@Param("ids") java.util.Collection<UUID> ids);

    @Query(value = "SELECT nextval('po_reference_seq')", nativeQuery = true)
    Long nextReferenceNumber();

    boolean existsByReference(String reference);
}
