package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Vendor;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    @Query("SELECT v FROM Vendor v WHERE " +
           "(:q = '' OR LOWER(v.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(COALESCE(v.primaryContactName, ''))  LIKE LOWER(CONCAT('%', :q, '%')) " +
           "         OR LOWER(COALESCE(v.primaryContactEmail, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:category IS NULL OR v.category = :category) AND " +
           "(:status   IS NULL OR v.status   = :status)")
    Page<Vendor> searchVendors(
        @Param("q") String q,
        @Param("category") Vendor.VendorCategory category,
        @Param("status")   Vendor.VendorStatus    status,
        Pageable pageable
    );

    long countByStatus(Vendor.VendorStatus status);
    long countByCategory(Vendor.VendorCategory category);

    @Query("SELECT v.category, COUNT(v) FROM Vendor v WHERE v.status = syncqubits.ai.skc.entity.Vendor.VendorStatus.ACTIVE GROUP BY v.category")
    List<Object[]> activeCountByCategory();

    /** Distinct tags across all vendors — drives the vendor tag picker. */
    @Query(value = "SELECT DISTINCT t FROM vendors v, unnest(v.tags) AS t " +
                   "WHERE v.tags IS NOT NULL AND array_length(v.tags, 1) > 0 ORDER BY t",
           nativeQuery = true)
    java.util.List<String> distinctTags();
}
