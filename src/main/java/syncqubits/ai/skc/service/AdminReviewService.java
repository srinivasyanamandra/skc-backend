package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.review.AdminInvitationSummary;
import syncqubits.ai.skc.dto.review.AdminReviewSummary;
import syncqubits.ai.skc.dto.review.InviteRequest;
import syncqubits.ai.skc.dto.review.InviteResponse;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.EmailTemplateRepository;
import syncqubits.ai.skc.repository.ReviewRepository;
import syncqubits.ai.skc.service.email.BrandedEmailLayout;
import syncqubits.ai.skc.util.NameUtils;
import syncqubits.ai.skc.util.TokenGenerator;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final ClientRepository clientRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Value("${review.link.default-expires-days:14}")
    private int defaultExpiresDays;

    @Value("${review.link.base-url:http://localhost:3000}")
    private String reviewBaseUrl;

    /* =================================================================== invite */

    @Transactional
    public InviteResponse invite(InviteRequest req) {
        Client client = resolveClient(req);

        int days = req.getExpiresInDays() == null ? defaultExpiresDays : req.getExpiresInDays();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(days, ChronoUnit.DAYS);

        String token = generateUniqueToken();

        Review invitation = Review.builder()
                .client(client)
                .type(Review.ReviewType.INVITATION)
                .eventType(req.getEventType())
                .eventDate(req.getEventDate())
                .token(token)
                .expiresAt(expiresAt)
                .sentAt(now)
                .build();
        invitation = reviewRepository.save(invitation);

        EmailTemplate template = resolveInvitationTemplate(req.getTemplateId());
        boolean usingDefault = false;
        if (template == null) {
            template = DefaultTemplates.reviewInvitation();
            usingDefault = true;
            log.info("No active REVIEW_INVITATION template in DB — using bundled default for invitation {}",
                    invitation.getId());
        }

        String reviewLink = buildReviewLink(token);
        String displayName = NameUtils.resolveDisplayName(client.getName(), client.getEmail());
        String firstName   = NameUtils.resolveFirstName(client.getName(), client.getEmail());

        /* Human-friendly date strings — the backend stores ISO 8601 instants
           but recipients want "22 June 2025", not "2025-06-22T18:30:00Z". */
        DateTimeFormatter prettyDate = DateTimeFormatter
                .ofPattern("d MMMM yyyy", Locale.ENGLISH)
                .withZone(ZoneId.of("Asia/Kolkata"));
        String prettyExpiry = prettyDate.format(expiresAt);
        String prettyEventDate = invitation.getEventDate() == null
                ? ""
                : invitation.getEventDate()
                    .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

        Map<String, Object> vars = new HashMap<>();
        vars.put("clientName",  displayName);
        vars.put("name",        displayName);
        vars.put("firstName",   firstName);
        vars.put("clientEmail", client.getEmail());
        vars.put("eventType",   invitation.getEventType());
        vars.put("eventDate",   prettyEventDate);
        vars.put("reviewLink",  reviewLink);
        vars.put("token",       token);
        vars.put("expiresAt",   prettyExpiry);
        // Brand-level placeholders consumed by BrandedEmailLayout's footer
        // ("© {{year}} {{brand}}…") and the body's signature line.
        vars.put("brand",       BrandedEmailLayout.BRAND_NAME);
        vars.put("year",        String.valueOf(Year.now().getValue()));

        Map<String, Object> details = new HashMap<>();
        details.put("clientId", client.getId().toString());
        details.put("clientEmail", client.getEmail());
        details.put("eventType", invitation.getEventType());
        details.put("eventDate", invitation.getEventDate().toString());
        details.put("expiresAt", expiresAt.toString());
        details.put("templateUsed", template.getName());
        systemLogService.logReview("invitation_created", "success", invitation.getId(), details);

        // Capture final values for the async lambda (must be effectively final)
        final UUID invitationId = invitation.getId();
        final EmailTemplate finalTemplate = template;
        log.info("Queuing invitation email to {} (template={}{})",
                client.getEmail(), finalTemplate.getName(), usingDefault ? " [bundled-default]" : "");

        emailService.sendAsync(finalTemplate, client.getEmail(), displayName,
                vars, "review", invitationId);

        return InviteResponse.builder()
                .id(invitationId)
                .clientId(client.getId())
                .reviewLink(reviewLink)
                .token(token)
                .expiresAt(expiresAt)
                .sentAt(now)
                .emailQueued(true)
                .emailDelivered(false)   // delivery is async; check system_logs for result
                .templateUsed(finalTemplate.getName())
                .build();
    }

    /* ================================================================== queries */

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewSummary> listReviews(String status,
                                                        Short minRating,
                                                        Boolean isPublic,
                                                        Boolean isFeatured,
                                                        String q,
                                                        int page,
                                                        int size,
                                                        String sortField,
                                                        Sort.Direction direction) {
        Review.ReviewStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Review> result = reviewRepository.adminSearchReviews(
                statusEnum, minRating, isPublic, isFeatured,
                q == null ? "" : q.trim(),
                PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toReviewSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInvitationSummary> listInvitations(Boolean used,
                                                                String q,
                                                                int page,
                                                                int size,
                                                                String sortField,
                                                                Sort.Direction direction) {
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Review> result = reviewRepository.adminSearchInvitations(
                used, q == null ? "" : q.trim(),
                PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toInvitationSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(UUID id) {
        Review r = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review " + id + " not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("type", r.getType().name().toLowerCase());
        out.put("eventType", r.getEventType());
        out.put("eventDate", r.getEventDate().toString());
        out.put("createdAt", r.getCreatedAt());

        Client c = r.getClient();
        out.put("client", Map.of(
                "id", c.getId().toString(),
                "name", c.getName() == null ? "" : c.getName(),
                "email", c.getEmail() == null ? "" : c.getEmail(),
                "phone", c.getPhone() == null ? "" : c.getPhone()));

        if (r.getType() == Review.ReviewType.INVITATION) {
            out.put("token", r.getToken());
            out.put("reviewLink", buildReviewLink(r.getToken()));
            out.put("sentAt", r.getSentAt());
            out.put("expiresAt", r.getExpiresAt());
            out.put("usedAt", r.getUsedAt());
            out.put("expired", r.getExpiresAt() != null && r.getExpiresAt().isBefore(Instant.now()));
            out.put("used", r.getUsedAt() != null);
        } else {
            out.put("invitationId", r.getInvitation() == null ? null : r.getInvitation().getId());
            out.put("reviewerName", r.getReviewerName());
            out.put("overallRating", r.getOverallRating());
            out.put("foodQualityRating", r.getFoodQualityRating());
            out.put("tasteRating", r.getTasteRating());
            out.put("presentationRating", r.getPresentationRating());
            out.put("staffBehaviorRating", r.getStaffBehaviorRating());
            out.put("timelinessRating", r.getTimelinessRating());
            out.put("serviceQualityRating", r.getServiceQualityRating());
            out.put("comments", r.getComments());
            out.put("suggestions", r.getSuggestions());
            out.put("recommend", r.getRecommend());
            out.put("status", r.getStatus() == null ? null : r.getStatus().name().toLowerCase());
            out.put("isFeatured", r.getIsFeatured());
            out.put("isPublic", r.getIsPublic());
            out.put("submittedAt", r.getSubmittedAt());
            out.put("moderatedAt", r.getModeratedAt());
        }
        return out;
    }

    /* =============================================================== moderation */

    @Transactional
    public AdminReviewSummary approve(UUID id) {
        Review r = loadSubmittedReview(id);
        r.setStatus(Review.ReviewStatus.APPROVED);
        r.setIsPublic(true);
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        systemLogService.logReview("approved", "success", r.getId(),
                Map.of("reviewerName", nullSafe(r.getReviewerName()), "rating", nullSafe(r.getOverallRating())));
        return toReviewSummary(r);
    }

    @Transactional
    public AdminReviewSummary reject(UUID id, String reason) {
        Review r = loadSubmittedReview(id);
        r.setStatus(Review.ReviewStatus.REJECTED);
        r.setIsPublic(false);
        r.setIsFeatured(false);
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        Map<String, Object> details = new HashMap<>();
        details.put("reviewerName", nullSafe(r.getReviewerName()));
        if (reason != null && !reason.isBlank()) details.put("reason", reason);
        systemLogService.logReview("rejected", "success", r.getId(), details);
        return toReviewSummary(r);
    }

    @Transactional
    public AdminReviewSummary toggleFeatured(UUID id, Boolean explicit) {
        Review r = loadSubmittedReview(id);
        if (r.getStatus() != Review.ReviewStatus.APPROVED) {
            throw new ConflictException("Only approved reviews can be featured.");
        }
        boolean target = explicit != null ? explicit : !Boolean.TRUE.equals(r.getIsFeatured());
        r.setIsFeatured(target);
        if (target && !Boolean.TRUE.equals(r.getIsPublic())) {
            r.setIsPublic(true);
        }
        reviewRepository.save(r);
        systemLogService.logReview(target ? "featured" : "unfeatured", "success", r.getId(),
                Map.of("reviewerName", nullSafe(r.getReviewerName())));
        return toReviewSummary(r);
    }

    @Transactional
    public void softDelete(UUID id) {
        Review r = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review " + id + " not found"));
        // soft-delete: mark unpublished + rejected, keep row for audit
        if (r.getType() == Review.ReviewType.REVIEW) {
            r.setStatus(Review.ReviewStatus.REJECTED);
            r.setIsPublic(false);
            r.setIsFeatured(false);
            r.setModeratedAt(Instant.now());
            reviewRepository.save(r);
        } else {
            // for invitations, mark used so the link is dead
            if (r.getUsedAt() == null) {
                r.setUsedAt(Instant.now());
                reviewRepository.save(r);
            }
        }
        systemLogService.logReview("deleted", "success", r.getId(),
                Map.of("type", r.getType().name().toLowerCase()));
    }

    /* ================================================================= helpers */

    private Client resolveClient(InviteRequest req) {
        if (req.getClientId() != null) {
            return clientRepository.findById(req.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client " + req.getClientId() + " not found"));
        }
        if (isBlank(req.getName()) || isBlank(req.getEmail()) || isBlank(req.getPhone())) {
            throw new BadRequestException("Either clientId or all of (name, email, phone) are required.");
        }
        // Derive clean name from provided name or email
        String derivedName = NameUtils.resolveDisplayName(req.getName(), req.getEmail());
        return clientRepository.findByEmail(req.getEmail()).orElseGet(() -> {
            log.info("Creating new client with derived name: {} for email: {}", derivedName, req.getEmail());
            return clientRepository.save(Client.builder()
                    .name(derivedName)
                    .email(req.getEmail())
                    .phone(req.getPhone())
                    .source("review_invite")
                    .status(Client.ClientStatus.LEAD)
                    .build());
        });
    }

    private EmailTemplate resolveInvitationTemplate(UUID explicitId) {
        if (explicitId != null) {
            EmailTemplate t = emailTemplateRepository.findById(explicitId)
                    .orElseThrow(() -> new ResourceNotFoundException("Template " + explicitId + " not found"));
            if (Boolean.FALSE.equals(t.getIsActive())) {
                throw new ConflictException("Template " + t.getName() + " is inactive.");
            }
            return t;
        }
        return emailTemplateRepository
                .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(EmailTemplate.TemplateType.REVIEW_INVITATION)
                .orElse(null);
    }

    private String generateUniqueToken() {
        for (int i = 0; i < 5; i++) {
            String t = TokenGenerator.urlSafe32();
            if (reviewRepository.findByToken(t).isEmpty()) return t;
        }
        throw new IllegalStateException("Failed to generate unique invitation token after 5 attempts");
    }

    /**
     * Build the public review-submission URL for an invitation token.
     *
     * <p>The frontend exposes the feedback form at {@code /feedback} under
     * path-based routing. Earlier hash-based routing used
     * {@code /#feedback?t=…}; the legacy form is still migrated client-side
     * by {@code useLegacyHashRedirect} so old emails keep working, but new
     * emails should ship the canonical path-based URL directly.
     *
     * <p>{@code review.link.base-url} should be set to the bare frontend
     * origin (e.g. {@code https://srikarthikeyacaterers.in}). Any trailing
     * slashes — and the legacy {@code /review} or {@code /feedback} segment
     * if mistakenly appended — are stripped here so the resulting URL is
     * always exactly one canonical {@code /feedback?t=<token>}.
     */
    private String buildReviewLink(String token) {
        String base = reviewBaseUrl == null ? "" : reviewBaseUrl.trim();
        // Strip trailing slashes
        base = base.replaceAll("/+$", "");
        // Defensive: if an older deployment still has REVIEW_LINK_BASE_URL
        // configured with a "/review" or "/feedback" suffix, drop it so we
        // don't double-up.
        base = base.replaceAll("(?i)/(review|feedback)/?$", "");
        return base + "/feedback?t=" + token;
    }

    private Review loadSubmittedReview(UUID id) {
        Review r = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review " + id + " not found"));
        if (r.getType() != Review.ReviewType.REVIEW) {
            throw new BadRequestException("Target is an invitation, not a review.");
        }
        return r;
    }

    private AdminReviewSummary toReviewSummary(Review r) {
        Client c = r.getClient();
        return AdminReviewSummary.builder()
                .id(r.getId())
                .clientId(c == null ? null : c.getId())
                .clientName(c == null ? null : c.getName())
                .clientEmail(c == null ? null : c.getEmail())
                .reviewerName(r.getReviewerName())
                .eventType(r.getEventType())
                .eventDate(r.getEventDate())
                .overallRating(r.getOverallRating())
                .comments(r.getComments())
                .suggestions(r.getSuggestions())
                .recommend(r.getRecommend())
                .status(r.getStatus() == null ? null : r.getStatus().name().toLowerCase())
                .isFeatured(r.getIsFeatured())
                .isPublic(r.getIsPublic())
                .moderatedAt(r.getModeratedAt())
                .submittedAt(r.getSubmittedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private AdminInvitationSummary toInvitationSummary(Review r) {
        Client c = r.getClient();
        Instant now = Instant.now();
        boolean expired = r.getExpiresAt() != null && r.getExpiresAt().isBefore(now);
        return AdminInvitationSummary.builder()
                .id(r.getId())
                .clientId(c == null ? null : c.getId())
                .clientName(c == null ? null : c.getName())
                .clientEmail(c == null ? null : c.getEmail())
                .eventType(r.getEventType())
                .eventDate(r.getEventDate())
                .token(r.getToken())
                .reviewLink(buildReviewLink(r.getToken()))
                .sentAt(r.getSentAt())
                .expiresAt(r.getExpiresAt())
                .usedAt(r.getUsedAt())
                .expired(expired)
                .used(r.getUsedAt() != null)
                .createdAt(r.getCreatedAt())
                .build();
    }

    private Review.ReviewStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Review.ReviewStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw + "'. Allowed: pending, approved, rejected.");
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static Object nullSafe(Object v) { return v == null ? "" : v; }
}
