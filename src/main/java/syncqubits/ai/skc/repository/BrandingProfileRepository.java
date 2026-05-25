package syncqubits.ai.skc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.BrandingProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Branding is singleton-by-convention. Callers should go through
 * {@code BrandingProfileService.getOrCreate()} rather than touching this
 * repository directly so the singleton invariant is preserved.
 */
@Repository
public interface BrandingProfileRepository extends JpaRepository<BrandingProfile, UUID> {

    /** The most recently updated row. Returned by the service as "the"
     *  branding profile. Existence is guaranteed by getOrCreate(). */
    Optional<BrandingProfile> findFirstByOrderByUpdatedAtDesc();
}
