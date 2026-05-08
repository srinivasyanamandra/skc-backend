package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.subscriber.SubscribeRequest;
import syncqubits.ai.skc.dto.subscriber.SubscribeResponse;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.entity.Subscriber;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.repository.EmailTemplateRepository;
import syncqubits.ai.skc.repository.SubscriberRepository;
import syncqubits.ai.skc.service.email.BrandedEmailLayout;
import syncqubits.ai.skc.service.email.TemplateVariables;
import syncqubits.ai.skc.util.NameUtils;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriberService {

    /** Stable code admins can use to override the default subscribe email copy. */
    public static final String SUBSCRIBE_TEMPLATE_CODE = "SUBSCRIBE";

    private final SubscriberRepository subscriberRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Transactional
    public SubscribeResponse subscribe(SubscribeRequest request) {
        log.info("Processing subscription request for email: {}", request.getEmail());

        String email = normaliseEmail(request.getEmail());
        String derivedName = NameUtils.resolveDisplayName(request.getName(), email);

        Subscriber subscriber;
        boolean isReactivation = false;

        var existing = subscriberRepository.findByEmail(email);
        if (existing.isPresent()) {
            subscriber = existing.get();
            if (Boolean.TRUE.equals(subscriber.getIsActive())) {
                // Active duplicate — reject loudly so the frontend can show a
                // distinct "already subscribed" message.
                throw new ConflictException("Email is already subscribed");
            }
            // Reactivate previously unsubscribed record.
            subscriber.setIsActive(true);
            subscriber.setUnsubscribedAt(null);
            subscriber.setName(derivedName);
            subscriber = subscriberRepository.save(subscriber);
            isReactivation = true;
            log.info("Reactivated subscription for: {} with name: {}", email, derivedName);
        } else {
            subscriber = subscriberRepository.save(Subscriber.builder()
                    .email(email)
                    .name(derivedName)
                    .source("website")
                    .isActive(true)
                    .build());
            log.info("New subscription created for: {} with name: {}", email, derivedName);
        }

        // Fire-and-forget welcome email — SMTP failures must never break the
        // public form. emailService.sendAsync hands off to the taskExecutor pool
        // so the response returns to the user immediately.
        sendWelcomeEmailSafely(subscriber, isReactivation);

        return SubscribeResponse.builder()
                .subscribed(true)
                .email(email)
                .build();
    }

    /* ─────────────────────────────────────────────────────────────────
       Welcome email orchestration
       ───────────────────────────────────────────────────────────────── */

    private void sendWelcomeEmailSafely(Subscriber subscriber, boolean isReactivation) {
        try {
            EmailTemplate template = resolveSubscribeTemplate();
            Map<String, Object> vars = buildVariables(subscriber);

            emailService.sendAsync(
                    template,
                    subscriber.getEmail(),
                    subscriber.getName(),
                    vars,
                    "subscriber",
                    subscriber.getId());

            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("recipientEmail", subscriber.getEmail());
            auditDetails.put("templateCode", SUBSCRIBE_TEMPLATE_CODE);
            auditDetails.put("templateSource", describeTemplateSource(template));
            auditDetails.put("reactivation", isReactivation);
            systemLogService.logEmail(
                    "subscribe_welcome_queued",
                    "success",
                    subscriber.getId(),
                    "subscriber",
                    auditDetails);

        } catch (Exception e) {
            // Never propagate — the subscription itself succeeded.
            log.error("Welcome email could not be queued for {}: {}",
                    subscriber.getEmail(), e.getMessage(), e);
            Map<String, Object> details = new HashMap<>();
            details.put("recipientEmail", subscriber.getEmail());
            details.put("error", e.getMessage());
            systemLogService.logEmail(
                    "subscribe_welcome_queue_failed",
                    "failed",
                    subscriber.getId(),
                    "subscriber",
                    details);
        }
    }

    /**
     * Three-tier resolution:
     * <ol>
     *   <li>Active template with code {@code SUBSCRIBE} (admin override).</li>
     *   <li>Latest active template of type {@code SUBSCRIBE_THANK_YOU} (legacy / type-keyed).</li>
     *   <li>Hard-coded {@link DefaultTemplates#subscribeThankYou()} fallback.</li>
     * </ol>
     */
    private EmailTemplate resolveSubscribeTemplate() {
        return emailTemplateRepository
                .findByCodeIgnoreCaseAndIsActiveTrue(SUBSCRIBE_TEMPLATE_CODE)
                .or(() -> emailTemplateRepository
                        .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(
                                EmailTemplate.TemplateType.SUBSCRIBE_THANK_YOU))
                .orElseGet(DefaultTemplates::subscribeThankYou);
    }

    private Map<String, Object> buildVariables(Subscriber s) {
        String displayName = NameUtils.resolveDisplayName(s.getName(), s.getEmail());
        String firstName   = NameUtils.resolveFirstName(s.getName(), s.getEmail());

        // Canonical placeholder set + subscriber-specific overrides.
        Map<String, Object> vars = TemplateVariables.liveSet();
        vars.put("clientName", displayName);
        vars.put("name",       displayName);
        vars.put("firstName",  firstName);
        vars.put("email",      s.getEmail());
        return vars;
    }

    private static String describeTemplateSource(EmailTemplate t) {
        if (t == null || t.getId() == null) return "default-fallback";
        if (SUBSCRIBE_TEMPLATE_CODE.equalsIgnoreCase(t.getCode())) return "code-match";
        return "type-match";
    }

    private static String normaliseEmail(String raw) {
        if (raw == null) return null;
        return raw.trim().toLowerCase();
    }
}
