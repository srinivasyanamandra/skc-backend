package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.EmailCampaign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, UUID> {

    @Query("SELECT c FROM EmailCampaign c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<EmailCampaign> searchCampaigns(
        @Param("status") EmailCampaign.CampaignStatus status,
        @Param("q") String query,
        Pageable pageable
    );

    List<EmailCampaign> findByStatusAndScheduledAtLessThanEqual(
        EmailCampaign.CampaignStatus status,
        Instant scheduledAt
    );

    @Query("SELECT COUNT(c) FROM EmailCampaign c WHERE c.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(c.sentCount), 0) FROM EmailCampaign c WHERE c.completedAt >= :since")
    long sumSentCountSince(@Param("since") Instant since);
}
