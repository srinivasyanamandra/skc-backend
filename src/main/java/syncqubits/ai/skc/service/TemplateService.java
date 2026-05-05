package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.email.EmailSendResult;
import syncqubits.ai.skc.dto.email.RenderedEmail;
import syncqubits.ai.skc.dto.template.TemplateRequest;
import syncqubits.ai.skc.dto.template.TemplateResponse;
import syncqubits.ai.skc.dto.template.TemplateTestRequest;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.EmailTemplateRepository;
import syncqubits.ai.skc.util.NameUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    public PageResponse<TemplateResponse> list(String q,
                                               String type,
                                               Boolean isActive,
                                               int page,
                                               int size,
                                               String sortField,
                                               Sort.Direction direction) {
        EmailTemplate.TemplateType typeEnum = parseType(type, false);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "updatedAt" : sortField);

        Page<EmailTemplate> result = emailTemplateRepository.adminSearch(
                q == null ? "" : q.trim(),
                typeEnum, isActive, PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toResponse);
    }

    public TemplateResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest req) {
        if (emailTemplateRepository.existsByName(req.getName())) {
            throw new ConflictException("Template with name '" + req.getName() + "' already exists.");
        }
        EmailTemplate.TemplateType type = parseType(req.getType(), true);

        EmailTemplate t = EmailTemplate.builder()
                .name(req.getName().trim())
                .type(type)
                .subject(req.getSubject())
                .preheader(req.getPreheader())
                .content(req.getContent())
                .isActive(req.getIsActive() == null ? true : req.getIsActive())
                .build();
        t = emailTemplateRepository.save(t);

        Map<String, Object> details = new HashMap<>();
        details.put("name", t.getName());
        details.put("type", t.getType().name().toLowerCase());
        systemLogService.logEmail("template_created", "success", t.getId(), "email_template", details);
        return toResponse(t);
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateRequest req) {
        EmailTemplate t = load(id);

        if (!t.getName().equals(req.getName())
                && emailTemplateRepository.existsByName(req.getName())) {
            throw new ConflictException("Template with name '" + req.getName() + "' already exists.");
        }
        EmailTemplate.TemplateType type = parseType(req.getType(), true);

        t.setName(req.getName().trim());
        t.setType(type);
        t.setSubject(req.getSubject());
        t.setPreheader(req.getPreheader());
        t.setContent(req.getContent());
        if (req.getIsActive() != null) t.setIsActive(req.getIsActive());

        emailTemplateRepository.save(t);

        Map<String, Object> details = new HashMap<>();
        details.put("name", t.getName());
        details.put("type", t.getType().name().toLowerCase());
        systemLogService.logEmail("template_updated", "success", t.getId(), "email_template", details);
        return toResponse(t);
    }

    @Transactional
    public void delete(UUID id) {
        EmailTemplate t = load(id);
        emailTemplateRepository.delete(t);
        Map<String, Object> details = new HashMap<>();
        details.put("name", t.getName());
        systemLogService.logEmail("template_deleted", "success", t.getId(), "email_template", details);
    }

    /**
     * Synchronous test send so the admin sees the SMTP result immediately
     * (success → messageId, failure → error code).
     */
    public Map<String, Object> testSend(UUID id, TemplateTestRequest req) {
        EmailTemplate t = load(id);
        if (Boolean.FALSE.equals(t.getIsActive())) {
            throw new ConflictException("Template is inactive; activate before testing.");
        }
        
        // Resolve clean display name for test recipient
        String displayName = NameUtils.resolveDisplayName(req.getName(), req.getTo());
        String firstName = NameUtils.resolveFirstName(req.getName(), req.getTo());
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("clientName", displayName);
        vars.put("name", displayName);
        vars.put("firstName", firstName);
        vars.put("email", req.getTo());
        vars.put("month", java.time.LocalDate.now().toString());
        vars.put("reviewLink", "https://example.invalid/test-link");
        vars.put("eventType", "wedding");
        vars.put("eventDate", java.time.LocalDate.now().plusDays(30).toString());
        vars.put("guestCount", "100");
        if (req.getVariables() != null) vars.putAll(req.getVariables());

        EmailSendResult result = emailService.sendNow(
                t, req.getTo(), displayName, vars, "email_template", t.getId());

        Map<String, Object> out = new HashMap<>();
        out.put("ok", result.isSuccess());
        out.put("templateId", t.getId().toString());
        out.put("attempts", result.getAttempts());
        if (result.getMessageId() != null) out.put("messageId", result.getMessageId());
        if (result.getError() != null) out.put("error", result.getError());
        return out;
    }

    /**
     * Convenience used by the campaign preview path so the wizard can see a
     * rendered template without sending.
     */
    public RenderedEmail render(UUID id, Map<String, Object> variables) {
        return emailService.render(load(id), variables == null ? Map.of() : variables);
    }

    /* ---------------------------------------------------------------- helpers */

    private EmailTemplate load(UUID id) {
        return emailTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template " + id + " not found"));
    }

    private TemplateResponse toResponse(EmailTemplate t) {
        return TemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .type(t.getType().name().toLowerCase())
                .subject(t.getSubject())
                .preheader(t.getPreheader())
                .content(t.getContent())
                .isActive(t.getIsActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private EmailTemplate.TemplateType parseType(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) throw new BadRequestException("type is required");
            return null;
        }
        try {
            return EmailTemplate.TemplateType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid template type '" + raw +
                    "'. Allowed: review_invitation, campaign, quote_confirmation, custom.");
        }
    }
}
