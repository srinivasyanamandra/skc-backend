package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Read-only "find due" — used by the scheduler to discover candidate
     * campaigns. The actual ownership is decided by {@link #tryClaim} which
     * runs an atomic {@code UPDATE … WHERE} so two concurrent schedulers
     * cannot both win the same id.
     */
    List<EmailCampaign> findByStatusAndScheduledAtLessThanEqual(
        EmailCampaign.CampaignStatus status,
        Instant scheduledAt
    );

    /**
     * Atomic claim of a single campaign for dispatch.
     *
     * Sets {@code status = SENDING}, stamps {@code locked_at} / {@code locked_by},
     * and sets {@code started_at} if it was null. The {@code WHERE} clause
     * accepts the row only when:
     *   - it is currently {@code QUEUED} (fresh claim) OR {@code SENDING} with a
     *     stale lock (a previous dispatcher crashed; auto-recovery), AND
     *   - {@code scheduled_at} is due, AND
     *   - the lock is either unset or older than {@code staleBefore}.
     *
     * The method returns the number of rows updated. {@code 1} = lock won;
     * {@code 0} = somebody else owns it (or it isn't due yet) — the caller
     * must bail without dispatching.
     *
     * @param id          campaign to claim
     * @param now         the current instant (passed in for testability)
     * @param staleBefore lock timestamps before this are considered stale
     *                    and may be reclaimed
     * @param instanceId  UUID of this JVM instance (audit trail)
     * @return            1 if claim succeeded, 0 if not
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailCampaign c " +
           "SET c.status = syncqubits.ai.skc.entity.EmailCampaign$CampaignStatus.SENDING, " +
           "    c.lockedAt = :now, " +
           "    c.lockedBy = :instanceId, " +
           "    c.startedAt = COALESCE(c.startedAt, :now) " +
           "WHERE c.id = :id " +
           "  AND c.scheduledAt <= :now " +
           "  AND (c.status = syncqubits.ai.skc.entity.EmailCampaign$CampaignStatus.QUEUED " +
           "       OR (c.status = syncqubits.ai.skc.entity.EmailCampaign$CampaignStatus.SENDING " +
           "           AND (c.lockedAt IS NULL OR c.lockedAt < :staleBefore)))")
    int tryClaim(
        @Param("id") UUID id,
        @Param("now") Instant now,
        @Param("staleBefore") Instant staleBefore,
        @Param("instanceId") String instanceId
    );

    /**
     * Release the dispatch lock — called on every exit path (success,
     * failure, mid-flight cancel detection). Idempotent: it's safe to call
     * even if the lock is already cleared. Does NOT change status; the
     * caller has already set the appropriate terminal status.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailCampaign c " +
           "SET c.lockedAt = NULL, c.lockedBy = NULL " +
           "WHERE c.id = :id")
    int releaseLock(@Param("id") UUID id);

    @Query("SELECT COUNT(c) FROM EmailCampaign c WHERE c.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(c.sentCount), 0) FROM EmailCampaign c WHERE c.completedAt >= :since")
    long sumSentCountSince(@Param("since") Instant since);
}
