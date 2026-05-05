package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import syncqubits.ai.skc.entity.EmailCampaign;
import syncqubits.ai.skc.repository.EmailCampaignRepository;

import java.time.Instant;
import java.util.List;

/**
 * Polls every {@code campaign.scheduler.poll-interval-ms} for queued campaigns whose
 * {@code scheduledAt <= now} and dispatches them. Single-tenant single-instance
 * design — no leader election needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {

    private final EmailCampaignRepository emailCampaignRepository;
    private final CampaignService campaignService;

    @Scheduled(
        fixedDelayString = "${campaign.scheduler.poll-interval-ms:30000}",
        initialDelayString = "${campaign.scheduler.initial-delay-ms:15000}"
    )
    public void tick() {
        Instant now = Instant.now();
        List<EmailCampaign> due = emailCampaignRepository
                .findByStatusAndScheduledAtLessThanEqual(EmailCampaign.CampaignStatus.QUEUED, now);

        if (due.isEmpty()) return;
        log.info("Scheduler: dispatching {} due campaign(s)", due.size());

        for (EmailCampaign c : due) {
            try {
                campaignService.dispatchAsync(c.getId());
            } catch (Exception e) {
                log.error("Scheduler: failed to queue campaign {} for dispatch", c.getId(), e);
            }
        }
    }
}
