package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.campaign.*;
import syncqubits.ai.skc.dto.email.EmailSendResult;
import syncqubits.ai.skc.dto.email.RenderedEmail;
import syncqubits.ai.skc.dto.email.SendOneRequest;
import syncqubits.ai.skc.dto.recipient.RecipientItem;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.EmailCampaign;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.entity.Subscriber;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.EmailCampaignRepository;
import syncqubits.ai.skc.repository.EmailTemplateRepository;
import syncqubits.ai.skc.repository.SubscriberRepository;

import syncqubits.ai.skc.util.NameUtils;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final EmailCampaignRepository emailCampaignRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final ClientRepository clientRepository;
    private final SubscriberRepository subscriberRepository;
    private final RecipientResolver recipientResolver;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Value("${review.link.base-url:http://localhost:3000}")
    private String reviewBaseUrl;

    @Value("${campaign.throttle.per-minute:120}")
    private int defaultThrottlePerMinute;

    @Value("${campaign.default-batch-size:50}")
    private int defaultBatchSize;

    /** Lock TTL for stale-dispatcher recovery (default 15 minutes). After
        this much wall-clock time, a SENDING campaign with no recent lock
        update is considered abandoned and may be re-claimed. */
    @Value("${campaign.scheduler.stale-lock-minutes:15}")
    private int staleLockMinutes;

    /** Stable identifier for this JVM instance; written to {@code locked_by}
        on every claim so we can audit which dispatcher worked which run. */
    private static final String INSTANCE_ID =
            "jvm-" + UUID.randomUUID().toString().substring(0, 8);

    /* ============================================================== queries */

    public PageResponse<CampaignSummary> list(String status, String q, int page, int size,
                                              String sortField, Sort.Direction direction) {
        EmailCampaign.CampaignStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);
        Page<EmailCampaign> result = emailCampaignRepository.searchCampaigns(
                statusEnum, q == null ? "" : q.trim(),
                PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toSummary);
    }

    public Map<String, Object> detail(UUID id, int recipientPage, int recipientSize) {
        EmailCampaign c = load(id);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("name", c.getName());
        out.put("status", c.getStatus().name().toLowerCase());
        out.put("defaultTemplateId", c.getDefaultTemplate() == null ? null : c.getDefaultTemplate().getId());
        out.put("globalVariables", c.getGlobalVariables());
        out.put("byKindTemplate", c.getConfig().get("byKindTemplate"));
        out.put("totalRecipients", c.getTotalRecipients());
        out.put("sentCount", c.getSentCount());
        out.put("failedCount", c.getFailedCount());
        out.put("scheduledAt", c.getScheduledAt());
        out.put("startedAt", c.getStartedAt());
        out.put("completedAt", c.getCompletedAt());
        out.put("createdAt", c.getCreatedAt());

        // recipients pagination (in-memory slice over the JSONB list)
        List<Map<String, Object>> all = c.getRecipients() == null ? List.of() : c.getRecipients();
        int from = Math.min(recipientPage * recipientSize, all.size());
        int to = Math.min(from + recipientSize, all.size());

        Map<String, Object> recipients = new LinkedHashMap<>();
        recipients.put("page", recipientPage);
        recipients.put("size", recipientSize);
        recipients.put("total", all.size());
        recipients.put("items", new ArrayList<>(all.subList(from, to)));
        out.put("recipients", recipients);

        return out;
    }

    /* ================================================================ create */

    @Transactional
    public CampaignSummary create(CampaignCreateRequest req) {
        EmailTemplate defaultTpl = null;
        if (req.getDefaultTemplateId() != null) {
            defaultTpl = emailTemplateRepository.findById(req.getDefaultTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template " + req.getDefaultTemplateId() + " not found"));
            if (Boolean.FALSE.equals(defaultTpl.getIsActive())) {
                throw new ConflictException("Default template is inactive");
            }
        }

        EmailCampaign c = EmailCampaign.builder()
                .name(req.getName())
                .status(EmailCampaign.CampaignStatus.DRAFT)
                .defaultTemplate(defaultTpl)
                .globalVariables(req.getGlobalVariables() == null ? Map.of() : req.getGlobalVariables())
                .config(new LinkedHashMap<>())
                .recipients(new ArrayList<>())
                .totalRecipients(0).sentCount(0).failedCount(0)
                .build();
        c = emailCampaignRepository.save(c);

        Map<String, Object> details = new HashMap<>();
        details.put("name", c.getName());
        if (defaultTpl != null) details.put("defaultTemplateId", defaultTpl.getId().toString());
        systemLogService.logCampaign("created", "success", c.getId(), details);

        return toSummary(c);
    }

    /* ============================================================ recipients */

    @Transactional
    public Map<String, Object> setRecipients(UUID id, CampaignRecipientsRequest req) {
        EmailCampaign c = load(id);
        ensureMutable(c);

        boolean append = "append".equalsIgnoreCase(req.getMode());
        ResolveRequest resolveReq = req.toResolveRequest();
        RecipientResolver.Materialised m = recipientResolver.materialise(resolveReq);

        List<Map<String, Object>> existing = (append && c.getRecipients() != null)
                ? new ArrayList<>(c.getRecipients()) : new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        for (Map<String, Object> r : existing) {
            Object email = r.get("email");
            if (email != null) seenEmails.add(email.toString().toLowerCase());
        }

        long appendedDuplicates = 0;
        for (RecipientItem item : m.items) {
            String key = item.getEmail() == null ? null : item.getEmail().toLowerCase();
            if (key != null && seenEmails.contains(key)) {
                appendedDuplicates++;
                continue;
            }
            existing.add(toRecipientEntry(item));
            if (key != null) seenEmails.add(key);
        }

        c.setRecipients(existing);
        c.setTotalRecipients(existing.size());
        emailCampaignRepository.save(c);

        long clients = existing.stream()
                .filter(e -> "client".equals(e.get("kind"))).count();
        long subs = existing.stream()
                .filter(e -> "subscriber".equals(e.get("kind"))).count();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("campaignId", c.getId());
        resp.put("totalRecipients", existing.size());
        Map<String, Long> byKind = new LinkedHashMap<>();
        byKind.put("clients", clients);
        byKind.put("subscribers", subs);
        resp.put("byKind", byKind);
        resp.put("deduplicated", m.deduplicated + appendedDuplicates);
        resp.put("skippedInactive", m.skippedInactive);
        resp.put("preview", existing.subList(0, Math.min(10, existing.size())));

        Map<String, Object> details = new HashMap<>();
        details.put("mode", append ? "append" : "replace");
        details.put("totalRecipients", existing.size());
        systemLogService.logCampaign("recipients_set", "success", c.getId(), details);

        return resp;
    }

    /* ============================================================== templates */

    @Transactional
    public Map<String, Object> assignTemplates(UUID id, CampaignTemplatesRequest req) {
        EmailCampaign c = load(id);
        ensureMutable(c);

        EmailTemplate def = emailTemplateRepository.findById(req.getDefaultTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template " + req.getDefaultTemplateId() + " not found"));
        if (Boolean.FALSE.equals(def.getIsActive())) {
            throw new ConflictException("Default template is inactive");
        }
        c.setDefaultTemplate(def);

        Map<String, Object> config = c.getConfig() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(c.getConfig());

        Map<String, UUID> byKind = req.getByKindTemplate();
        if (byKind != null && !byKind.isEmpty()) {
            Map<String, String> validated = new LinkedHashMap<>();
            for (Map.Entry<String, UUID> entry : byKind.entrySet()) {
                EmailTemplate t = emailTemplateRepository.findById(entry.getValue())
                        .orElseThrow(() -> new ConflictException("Template " + entry.getValue() + " not found"));
                if (Boolean.FALSE.equals(t.getIsActive())) {
                    throw new ConflictException("Template " + t.getName() + " is inactive");
                }
                validated.put(entry.getKey().toLowerCase(), t.getId().toString());
            }
            config.put("byKindTemplate", validated);
        } else {
            config.remove("byKindTemplate");
        }

        // per-recipient overrides — patch existing recipient JSON entries by id+kind
        int overrideCount = 0;
        if (req.getPerRecipient() != null && !req.getPerRecipient().isEmpty()) {
            // validate referenced templates first
            Set<UUID> tplIds = new HashSet<>();
            for (CampaignTemplatesRequest.PerRecipient pr : req.getPerRecipient()) {
                tplIds.add(pr.getTemplateId());
            }
            Map<UUID, EmailTemplate> tplMap = new HashMap<>();
            for (EmailTemplate t : emailTemplateRepository.findAllById(tplIds)) {
                if (Boolean.FALSE.equals(t.getIsActive())) {
                    throw new ConflictException("Template " + t.getName() + " is inactive");
                }
                tplMap.put(t.getId(), t);
            }
            for (UUID tid : tplIds) {
                if (!tplMap.containsKey(tid)) {
                    throw new ConflictException("Template " + tid + " not found");
                }
            }

            List<Map<String, Object>> recipients = c.getRecipients() == null
                    ? new ArrayList<>() : new ArrayList<>(c.getRecipients());

            for (CampaignTemplatesRequest.PerRecipient pr : req.getPerRecipient()) {
                String key = pr.getKind().toLowerCase() + ":" + pr.getId();
                boolean matched = false;
                for (Map<String, Object> entry : recipients) {
                    String entryKey = (entry.get("kind") + ":" + entry.get("id")).toLowerCase();
                    if (entryKey.equals(key)) {
                        entry.put("templateId", pr.getTemplateId().toString());
                        if (pr.getVariables() != null && !pr.getVariables().isEmpty()) {
                            entry.put("variables", pr.getVariables());
                        }
                        matched = true;
                        overrideCount++;
                        break;
                    }
                }
                if (!matched) {
                    log.warn("perRecipient override {}/{} did not match any existing recipient on campaign {}",
                            pr.getKind(), pr.getId(), c.getId());
                }
            }
            c.setRecipients(recipients);
        }

        c.setConfig(config);
        emailCampaignRepository.save(c);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("campaignId", c.getId());
        resp.put("defaultTemplateId", def.getId());
        if (config.containsKey("byKindTemplate")) resp.put("byKindTemplate", config.get("byKindTemplate"));
        resp.put("overrideCount", overrideCount);
        resp.put("templatesValidated", true);

        Map<String, Object> details = new HashMap<>();
        details.put("defaultTemplateId", def.getId().toString());
        details.put("overrideCount", overrideCount);
        systemLogService.logCampaign("templates_assigned", "success", c.getId(), details);

        return resp;
    }

    /* ============================================================== preview */

    public Map<String, Object> preview(UUID id, CampaignPreviewRequest req) {
        EmailCampaign c = load(id);
        if (c.getRecipients() == null || c.getRecipients().isEmpty()) {
            throw new BadRequestException("Campaign has no recipients yet.");
        }

        List<Map<String, Object>> targets = new ArrayList<>();
        if (req.getRecipient() != null) {
            String key = req.getRecipient().getKind().toLowerCase() + ":" + req.getRecipient().getId();
            for (Map<String, Object> entry : c.getRecipients()) {
                String entryKey = (entry.get("kind") + ":" + entry.get("id")).toLowerCase();
                if (entryKey.equals(key)) { targets.add(entry); break; }
            }
            if (targets.isEmpty()) {
                throw new ResourceNotFoundException("Recipient not in campaign list");
            }
        } else {
            int n = req.getSample() == null ? 1 : Math.min(req.getSample(), c.getRecipients().size());
            List<Map<String, Object>> shuffled = new ArrayList<>(c.getRecipients());
            Collections.shuffle(shuffled, new Random());
            targets.addAll(shuffled.subList(0, n));
        }

        List<Map<String, Object>> renders = new ArrayList<>();
        for (Map<String, Object> entry : targets) {
            EmailTemplate template = resolveTemplateForEntry(c, entry);
            Map<String, Object> vars = mergeVariables(c, entry);
            RenderedEmail rendered = emailService.render(template, vars);

            Map<String, Object> render = new LinkedHashMap<>();
            Map<String, Object> recipient = new LinkedHashMap<>();
            recipient.put("kind", entry.get("kind"));
            recipient.put("id", entry.get("id"));
            recipient.put("email", entry.get("email"));
            recipient.put("name", entry.get("name"));
            render.put("recipient", recipient);

            Map<String, Object> tpl = new LinkedHashMap<>();
            tpl.put("id", template.getId());
            tpl.put("name", template.getName());
            render.put("template", tpl);

            render.put("subject", rendered.getSubject());
            render.put("preheader", rendered.getPreheader());
            render.put("html", rendered.getHtml());
            render.put("text", rendered.getText());
            render.put("variablesResolved", rendered.getVariablesResolved());
            renders.add(render);
        }
        return Map.of("renders", renders);
    }

    /* ============================================================ send/cancel */

    @Transactional
    public Map<String, Object> send(UUID id, CampaignSendRequest req) {
        EmailCampaign c = load(id);
        if (c.getStatus() == EmailCampaign.CampaignStatus.SENT
                || c.getStatus() == EmailCampaign.CampaignStatus.SENDING
                || c.getStatus() == EmailCampaign.CampaignStatus.QUEUED) {
            throw new ConflictException("Campaign already " + c.getStatus().name().toLowerCase());
        }
        if (c.getRecipients() == null || c.getRecipients().isEmpty()) {
            throw new BadRequestException("Campaign has no recipients.");
        }
        if (c.getDefaultTemplate() == null) {
            throw new BadRequestException("Campaign has no default template.");
        }

        Instant scheduleAt = req == null ? null : req.getScheduleAt();
        boolean immediate = scheduleAt == null || !scheduleAt.isAfter(Instant.now());

        c.setStatus(EmailCampaign.CampaignStatus.QUEUED);
        c.setScheduledAt(immediate ? Instant.now() : scheduleAt);
        c.setSentCount(0);
        c.setFailedCount(0);

        if (req != null && req.getThrottle() != null) {
            Map<String, Object> cfg = c.getConfig() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(c.getConfig());
            cfg.put("throttle", req.getThrottle());
            c.setConfig(cfg);
        }
        emailCampaignRepository.save(c);

        Map<String, Object> details = new HashMap<>();
        details.put("totalRecipients", c.getTotalRecipients());
        details.put("scheduledAt", c.getScheduledAt().toString());
        details.put("immediate", immediate);
        systemLogService.logCampaign("queued", "success", c.getId(), details);

        if (immediate) {
            // dispatch in background — caller gets 202 immediately
            dispatchAsync(c.getId());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("campaignId", c.getId());
        resp.put("status", c.getStatus().name().toLowerCase());
        resp.put("totalRecipients", c.getTotalRecipients());
        resp.put("scheduledFor", c.getScheduledAt());
        return resp;
    }

    @Transactional
    public Map<String, Object> cancel(UUID id) {
        EmailCampaign c = load(id);
        if (c.getStatus() != EmailCampaign.CampaignStatus.QUEUED
                && c.getStatus() != EmailCampaign.CampaignStatus.SENDING) {
            throw new ConflictException("Only queued or sending campaigns can be cancelled.");
        }
        c.setStatus(EmailCampaign.CampaignStatus.CANCELLED);
        c.setCompletedAt(Instant.now());
        emailCampaignRepository.save(c);

        systemLogService.logCampaign("cancelled", "success", c.getId(),
                Map.of("sentCount", c.getSentCount(), "failedCount", c.getFailedCount()));

        return Map.of(
                "campaignId", c.getId(),
                "status", "cancelled",
                "sentCount", c.getSentCount(),
                "failedCount", c.getFailedCount());
    }

    /* ============================================================ dispatch loop */

    @Async
    public void dispatchAsync(UUID id) {
        try {
            dispatch(id);
        } catch (Exception e) {
            log.error("Campaign {} dispatch failed", id, e);
        }
    }

    /**
     * Iterates the materialised recipient list, sending each email via
     * {@link EmailService}. Per-batch saves persist {@code sentCount} /
     * {@code failedCount} and per-recipient delivery state.
     *
     * <h3>Concurrency / idempotency</h3>
     * The first thing this method does is an atomic
     * {@link EmailCampaignRepository#tryClaim} — a single SQL {@code UPDATE
     * … WHERE locked_at IS NULL OR locked_at < :staleBefore}. If 0 rows
     * are affected, another dispatcher (or another instance) already owns
     * this campaign and we return without sending anything. This makes
     * {@code dispatchAsync} idempotent: invoking it twice is harmless.
     *
     * <h3>Subscriber freshness</h3>
     * Audiences are snapshotted at campaign creation time. By the time the
     * dispatcher runs, individual subscribers may have unsubscribed. Before
     * each send to a {@code kind=='subscriber'} recipient, we re-read the
     * subscriber and skip them with reason {@code subscriber_unsubscribed}
     * if {@code is_active=false}. This honours the unsubscribe contract
     * even for queued campaigns.
     *
     * <h3>Cancellation</h3>
     * Between batches, we re-read the campaign and bail if status was set
     * to {@code CANCELLED}. The lock is then released on the way out.
     *
     * <h3>Resume safety</h3>
     * Each per-recipient delivery state lives in the JSONB column. On
     * re-dispatch (after a crash or stale-lock recovery), entries with
     * {@code deliveryStatus='sent'} are skipped — at-most-once per recipient.
     */
    public void dispatch(UUID id) {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofMinutes(Math.max(1, staleLockMinutes)));

        // Atomic claim — single UPDATE … WHERE. Returns 1 if we won the
        // lock, 0 if somebody else holds it (or the campaign isn't due).
        int claimed = emailCampaignRepository.tryClaim(id, now, staleBefore, INSTANCE_ID);
        if (claimed == 0) {
            log.info("Campaign {} not eligible for dispatch by {} (already claimed or not due)",
                    id, INSTANCE_ID);
            return;
        }
        log.info("Campaign {} claimed by dispatcher {}", id, INSTANCE_ID);

        boolean releaseLockOnExit = true;
        try {
            EmailCampaign c = emailCampaignRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign " + id + " not found"));

            int throttlePerMinute = throttleFor(c);
            long throttleDelayMs = throttlePerMinute > 0 ? Math.max(0, 60_000L / throttlePerMinute) : 0;

            List<Map<String, Object>> recipients = c.getRecipients() == null
                    ? new ArrayList<>() : new ArrayList<>(c.getRecipients());

            int sent    = c.getSentCount()   == null ? 0 : c.getSentCount();
            int failed  = c.getFailedCount() == null ? 0 : c.getFailedCount();
            int skipped = 0;
            int batchSize = Math.max(1, defaultBatchSize);
            int sinceLastSave = 0;

            for (int i = 0; i < recipients.size(); i++) {
                /* Cancel check at every batch boundary — cheap and gives a
                   bounded-latency stop on admin Cancel actions. */
                if (sinceLastSave == 0) {
                    EmailCampaign refreshed = emailCampaignRepository.findById(id).orElse(null);
                    if (refreshed == null
                            || refreshed.getStatus() == EmailCampaign.CampaignStatus.CANCELLED) {
                        log.info("Campaign {} cancelled mid-flight; stopping at index {}", id, i);
                        return; // finally{} releases the lock
                    }
                }

                Map<String, Object> entry = recipients.get(i);
                String prevDelivery = (String) entry.get("deliveryStatus");

                /* Resume-safe: if a previous run already sent to this
                   recipient (e.g. the dispatcher crashed mid-flight and
                   we're a stale-lock recovery), skip them. At-most-once
                   per recipient per campaign. */
                if ("sent".equals(prevDelivery) || "skipped".equals(prevDelivery)) {
                    continue;
                }

                /* Subscriber-freshness check — honour unsubscribes that
                   happened between schedule time and send time. */
                if ("subscriber".equalsIgnoreCase(String.valueOf(entry.get("kind")))) {
                    Object idObj = entry.get("id");
                    if (idObj != null) {
                        try {
                            UUID sid = UUID.fromString(idObj.toString());
                            Subscriber s = subscriberRepository.findById(sid).orElse(null);
                            if (s == null || !Boolean.TRUE.equals(s.getIsActive())) {
                                entry.put("deliveryStatus", "skipped");
                                entry.put("skipReason", "subscriber_unsubscribed");
                                skipped++;
                                sinceLastSave++;
                                if (sinceLastSave >= batchSize || i == recipients.size() - 1) {
                                    c.setRecipients(recipients);
                                    c.setSentCount(sent);
                                    c.setFailedCount(failed);
                                    emailCampaignRepository.save(c);
                                    sinceLastSave = 0;
                                }
                                continue;
                            }
                        } catch (IllegalArgumentException ignore) { /* malformed id; fall through to send attempt */ }
                    }
                }

                try {
                    EmailTemplate template = resolveTemplateForEntry(c, entry);
                    Map<String, Object> vars = mergeVariables(c, entry);
                    RenderedEmail rendered = emailService.render(template, vars);

                    String email = (String) entry.get("email");
                    String name  = (String) entry.get("name");

                    EmailSendResult result = emailService.sendRendered(
                            rendered, email, name, template.getId(), "email_campaign", c.getId());

                    if (result.isSuccess()) {
                        entry.put("deliveryStatus", "sent");
                        entry.put("sentAt", Instant.now().toString());
                        if (result.getMessageId() != null) entry.put("messageId", result.getMessageId());
                        entry.remove("error");
                        sent++;
                    } else {
                        entry.put("deliveryStatus", "failed");
                        entry.put("error", result.getError() == null ? "send_failed" : result.getError());
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("Campaign {} send to entry {} failed", id, entry.get("email"), e);
                    entry.put("deliveryStatus", "failed");
                    entry.put("error", e.getMessage());
                    failed++;
                }

                sinceLastSave++;
                if (sinceLastSave >= batchSize || i == recipients.size() - 1) {
                    c.setRecipients(recipients);
                    c.setSentCount(sent);
                    c.setFailedCount(failed);
                    emailCampaignRepository.save(c);
                    sinceLastSave = 0;
                }

                if (throttleDelayMs > 0) {
                    try { Thread.sleep(throttleDelayMs); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Campaign {} dispatch interrupted; lock will be released", id);
                        break;
                    }
                }
            }

            EmailCampaign refreshed = emailCampaignRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign vanished mid-flight"));
            if (refreshed.getStatus() == EmailCampaign.CampaignStatus.CANCELLED) {
                return; // finally releases lock
            }

            boolean anySuccess = sent > 0;
            boolean allFailed  = failed > 0 && sent == 0 && skipped == 0 && !recipients.isEmpty();

            refreshed.setRecipients(recipients);
            refreshed.setSentCount(sent);
            refreshed.setFailedCount(failed);
            refreshed.setCompletedAt(Instant.now());
            refreshed.setStatus(allFailed
                    ? EmailCampaign.CampaignStatus.FAILED
                    : EmailCampaign.CampaignStatus.SENT);
            emailCampaignRepository.save(refreshed);

            Map<String, Object> details = new HashMap<>();
            details.put("sentCount", sent);
            details.put("failedCount", failed);
            details.put("skippedCount", skipped);
            details.put("totalRecipients", recipients.size());
            details.put("dispatcherInstanceId", INSTANCE_ID);
            systemLogService.logCampaign(
                    refreshed.getStatus() == EmailCampaign.CampaignStatus.SENT ? "completed" : "failed",
                    anySuccess ? "success" : "failed",
                    refreshed.getId(), details);

            if (skipped > 0) {
                log.info("Campaign {} completed: sent={}, failed={}, skipped={} (unsubscribed at send time)",
                        id, sent, failed, skipped);
            }
        } finally {
            if (releaseLockOnExit) {
                try {
                    emailCampaignRepository.releaseLock(id);
                } catch (Exception releaseEx) {
                    /* Lock release failure is non-fatal — the stale-lock
                       TTL will recover the row. Log it for visibility. */
                    log.warn("Failed to release dispatch lock on campaign {}: {}",
                            id, releaseEx.getMessage());
                }
            }
        }
    }

    /* ============================================================ send-one */

    public Map<String, Object> sendOne(SendOneRequest req) {
        EmailTemplate template = emailTemplateRepository.findById(req.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template " + req.getTemplateId() + " not found"));
        if (Boolean.FALSE.equals(template.getIsActive())) {
            throw new ConflictException("Template is inactive");
        }

        ResolveRequest.RecipientRef to = req.getTo();
        String email, name; UUID entityId;
        String entityType;

        if ("client".equalsIgnoreCase(to.getKind())) {
            Client c = clientRepository.findById(to.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client " + to.getId() + " not found"));
            email = c.getEmail();
            name = c.getName();
            entityId = c.getId();
            entityType = "client";
        } else if ("subscriber".equalsIgnoreCase(to.getKind())) {
            Subscriber s = subscriberRepository.findById(to.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subscriber " + to.getId() + " not found"));
            if (Boolean.FALSE.equals(s.getIsActive())) {
                throw new ConflictException("Subscriber is unsubscribed");
            }
            email = s.getEmail();
            name = s.getName();
            entityId = s.getId();
            entityType = "subscriber";
        } else {
            throw new BadRequestException("to.kind must be 'client' or 'subscriber'");
        }

        Map<String, Object> vars = new HashMap<>();
        String displayName = NameUtils.resolveDisplayName(name, email);
        String firstName   = NameUtils.resolveFirstName(name, email);
        vars.put("clientName", displayName);
        vars.put("name",       displayName);
        vars.put("firstName",  firstName);
        vars.put("email",      email);
        vars.put("month", java.time.LocalDate.now().toString());
        if (req.getVariables() != null) vars.putAll(req.getVariables());

        EmailSendResult result = emailService.sendNow(template, email, displayName, vars, entityType, entityId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", result.isSuccess());
        resp.put("templateId", template.getId());
        resp.put("attempts", result.getAttempts());
        if (result.getMessageId() != null) resp.put("messageId", result.getMessageId());
        if (result.getError() != null) resp.put("error", result.getError());
        return resp;
    }

    /* ============================================================ helpers */

    private EmailTemplate resolveTemplateForEntry(EmailCampaign c, Map<String, Object> entry) {
        // 1) per-recipient override
        Object explicit = entry.get("templateId");
        if (explicit != null) {
            try {
                UUID tid = UUID.fromString(explicit.toString());
                EmailTemplate t = emailTemplateRepository.findById(tid)
                        .orElseThrow(() -> new ConflictException("per-recipient template " + tid + " missing"));
                if (Boolean.FALSE.equals(t.getIsActive())) {
                    throw new ConflictException("per-recipient template " + t.getName() + " is inactive");
                }
                return t;
            } catch (IllegalArgumentException ignored) { /* fall through */ }
        }

        // 2) byKindTemplate
        Object byKindObj = c.getConfig() == null ? null : c.getConfig().get("byKindTemplate");
        if (byKindObj instanceof Map<?, ?> byKindMap) {
            Object kindTpl = byKindMap.get(String.valueOf(entry.get("kind")));
            if (kindTpl != null) {
                try {
                    UUID tid = UUID.fromString(kindTpl.toString());
                    EmailTemplate t = emailTemplateRepository.findById(tid)
                            .orElseThrow(() -> new ConflictException("byKind template " + tid + " missing"));
                    if (Boolean.FALSE.equals(t.getIsActive())) {
                        throw new ConflictException("byKind template " + t.getName() + " is inactive");
                    }
                    return t;
                } catch (IllegalArgumentException ignored) { /* fall through */ }
            }
        }

        // 3) defaultTemplate
        if (c.getDefaultTemplate() == null) {
            throw new ConflictException("Campaign has no default template");
        }
        return c.getDefaultTemplate();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeVariables(EmailCampaign c, Map<String, Object> entry) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (c.getGlobalVariables() != null) vars.putAll(c.getGlobalVariables());

        Object overrides = entry.get("variables");
        if (overrides instanceof Map<?, ?> ov) {
            ov.forEach((k, v) -> vars.put(String.valueOf(k), v));
        }

        // Resolve a clean display name — prefer stored name, derive from email if absent
        String rawName  = entry.get("name")  == null ? null : entry.get("name").toString();
        String rawEmail = entry.get("email") == null ? null : entry.get("email").toString();
        String displayName = NameUtils.resolveDisplayName(rawName, rawEmail);
        String firstName   = NameUtils.resolveFirstName(rawName, rawEmail);

        vars.put("clientName", displayName);
        vars.put("name",       displayName);
        vars.put("firstName",  firstName);
        vars.put("email",      rawEmail == null ? "" : rawEmail);
        vars.put("kind",       entry.get("kind"));
        vars.put("reviewLink", reviewBaseUrl);
        vars.put("campaignId",   c.getId().toString());
        vars.put("campaignName", c.getName());

        return vars;
    }

    private Map<String, Object> toRecipientEntry(RecipientItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", item.getKind());
        m.put("id", item.getId().toString());
        m.put("email", item.getEmail());
        m.put("name", item.getName());
        m.put("deliveryStatus", "pending");
        return m;
    }

    private CampaignSummary toSummary(EmailCampaign c) {
        return CampaignSummary.builder()
                .id(c.getId())
                .name(c.getName())
                .status(c.getStatus().name().toLowerCase())
                .totalRecipients(c.getTotalRecipients())
                .sentCount(c.getSentCount())
                .failedCount(c.getFailedCount())
                .scheduledAt(c.getScheduledAt())
                .startedAt(c.getStartedAt())
                .completedAt(c.getCompletedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private EmailCampaign load(UUID id) {
        return emailCampaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign " + id + " not found"));
    }

    private void ensureMutable(EmailCampaign c) {
        if (c.getStatus() != EmailCampaign.CampaignStatus.DRAFT) {
            throw new ConflictException("Campaign is " + c.getStatus().name().toLowerCase()
                    + " — cannot mutate after queuing.");
        }
    }

    private int throttleFor(EmailCampaign c) {
        if (c.getConfig() != null && c.getConfig().get("throttle") instanceof Map<?, ?> t) {
            Object v = t.get("perMinute");
            if (v instanceof Number n) return n.intValue();
            if (v != null) {
                try { return Integer.parseInt(v.toString()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return defaultThrottlePerMinute;
    }

    private EmailCampaign.CampaignStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return EmailCampaign.CampaignStatus.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw +
                    "'. Allowed: draft, queued, sending, sent, failed, cancelled.");
        }
    }
}
