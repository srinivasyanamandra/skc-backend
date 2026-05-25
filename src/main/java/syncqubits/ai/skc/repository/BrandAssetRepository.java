package syncqubits.ai.skc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.BrandAsset;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrandAssetRepository extends JpaRepository<BrandAsset, UUID> {

    /** All assets, newest first — the gallery default. */
    List<BrandAsset> findAllByOrderByCreatedAtDesc();

    /** Assets for a specific role (logo, signature, QR…) ordered newest
     *  first so the most recent upload wins ties. */
    List<BrandAsset> findByRoleOrderByCreatedAtDesc(String role);
}
