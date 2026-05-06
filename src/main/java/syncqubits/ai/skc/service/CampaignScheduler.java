package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import syncqubits.ai.skc.entity.EmailCampaign;
import syncqubits.ai.skc.repository.EmailCampaignRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls every {@code campaign.scheduler.poll-interval-ms} for queued campaigns
 * whose {@code scheduledAt <= now} and hands each one to the dispatcher.
 *
 * <h3>Concurrency model</h3>
 * The scheduler does <strong>not</strong> claim ownership itself — it just
 * surfaces candidate ids and hands each off to {@link CampaignService#dispatchAsync}.
 * The atomic claim is performed inside {@code dispatch()} via
 * {@link EmailCampaignRepository#tryClaim} (single-statement {@code UPDATE …
 * WHERE locked_at IS NULL OR locked_at < :staleBefore}). This makes it safe
 * for two scheduler ticks (overlap during slow tick, or two instances) to
 * both invoke {@code dispatchAsync} for the same id — only one wins the
 * lock; the other no-ops.
 *
 * <h3>Render free-tier resilience</h3>
 * On Render free tier the JVM sleeps after 15 minutes idle. When traffic
 * resumes, the scheduler wakes and may find campaigns whose
 * {@code scheduledAt} is in the deep past. Each tick reports the
 * <em>maximum lateness</em> at WARN level so operations can detect cold-start
 * dispatch drift after the fact (and so a downstream alert can page on it).
 *
 * <h3>Stale-lock recovery</h3>
 * If a previous dispatcher crashed mid-flight, its row will sit in
 * {@code SENDING} status with an old {@code locked_at}. After
 * {@code campaign.scheduler.stale-lock-minutes} (default 15), the dispatcher
 * reclaims the row automatically via the same {@code tryClaim} path. The
 * dispatch loop is resume-safe — recipients with {@code deliveryStatus=='sent'}
 * are skipped on retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {

    private final EmailCampaignRepository emailCampaignRepository;
    private final CampaignService campaignService;

    /** Campaigns starting more than this many seconds late get a WARN log. */
    @Value("${campaign.scheduler.late-warn-seconds:120}")
    private long lateWarnSeconds;

    @Scheduled(
        fixedDelayString = "${campaign.scheduler.poll-interval-ms:30000}",
        initialDelayString = "${campaign.scheduler.initial-delay-ms:15000}"
    )
    public void tick() {
        Instant now = Instant.now();

        List<EmailCampaign> due = emailCampaignRepository
                .findByStatusAndScheduledAtLessThanEqual(EmailCampaign.CampaignStatus.QUEUED, now);

        if (due.isEmpty()) return;

        // Late-send detection — visible in operational logs so cold-start
        // drift is detectable without manual database queries.
        long maxLateSeconds = due.stream()
                .filter(c -> c.getScheduledAt() != null)
                .mapToLong(c -> Math.max(0, Duration.between(c.getScheduledAt(), now).getSeconds()))
                .max().orElse(0);

        if (maxLateSeconds > lateWarnSeconds) {
            log.warn(
                "Scheduler: dispatching {} due campaign(s); worst lateness {}s — possible cold-start drift",
                due.size(), maxLateSeconds);
            for (EmailCampaign c : due) {
                if (c.getScheduledAt() == null) continue;
                long late = Duration.between(c.getScheduledAt(), now).getSeconds();
                if (late > lateWarnSeconds) {
                    log.warn("  └─ campaign {} (\"{}\") is {}s late (scheduled {}, now {})",
                            c.getId(), c.getName(), late, c.getScheduledAt(), now);
                }
            }
        } else {
            log.info("Scheduler: dispatching {} due campaign(s)", due.size());
        }

        for (EmailCampaign c : due) {
            try {
                // dispatchAsync is idempotent — if another tick or instance
                // already won the lock, the dispatch() will see the lock is
                // held by someone else and bail without sending.
                campaignService.dispatchAsync(c.getId());
            } catch (Exception e) {
                log.error("Scheduler: failed to enqueue campaign {} for dispatch", c.getId(), e);
            }
        }
    }
}
