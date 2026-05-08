package syncqubits.ai.skc.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import syncqubits.ai.skc.dto.email.EmailSendResult;
import syncqubits.ai.skc.dto.email.RenderedEmail;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.service.email.BlockHtmlRenderer;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {{var}} email templates and sends them through Hostinger SMTP.
 * Send is async and retry-safe; every attempt writes a row into system_logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private final JavaMailSender mailSender;
    private final SystemLogService systemLogService;
    private final BlockHtmlRenderer blockHtmlRenderer;

    @Value("${mail.from}")
    private String fromAddress;

    @Value("${mail.from-name}")
    private String fromName;

    @Value("${mail.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${mail.retry.backoff-ms:1000}")
    private long backoffMs;

    /* ------------------------------------------------------------------ render */

    public RenderedEmail render(EmailTemplate template, Map<String, Object> variables) {
        if (template == null) {
            throw new IllegalArgumentException("template is required");
        }
        Map<String, Object> resolved = variables == null ? Map.of() : variables;
        Map<String, Object> content = template.getContent() == null ? Map.of() : template.getContent();

        String subject   = substitute(template.getSubject(),   resolved);
        String preheader = substitute(template.getPreheader(), resolved);

        // Prefer rendering from `content.blocks` when available — it's the
        // editor's source of truth and is guaranteed to carry raw `{{var}}`
        // placeholders. The persisted `content.html` may have been baked
        // by an earlier version of the EmailBuilder that pre-resolved
        // placeholders before saving (the "[First Name]" bug); regenerating
        // server-side from blocks side-steps that bug entirely. Falls back
        // to `content.html` only when blocks are missing (e.g. legacy
        // templates that were always raw HTML).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (content.get("blocks") instanceof List)
                ? (List<Map<String, Object>>) content.get("blocks")
                : null;

        String html;
        String text;
        if (blocks != null && !blocks.isEmpty()) {
            String body = blockHtmlRenderer.renderBody(blocks);
            html = blockHtmlRenderer.wrapBranded(body, subject, preheader);
            text = blockHtmlRenderer.renderText(blocks);
            html = substitute(html, resolved);
            text = substitute(text, resolved);
        } else {
            html = substitute(asString(content.get("html")), resolved);
            text = substitute(asString(content.get("text")), resolved);
        }

        // If no html given but text provided, wrap text into a minimal html body
        if (isBlank(html) && !isBlank(text)) {
            html = "<pre style=\"font-family:Inter,Arial,sans-serif;font-size:15px;white-space:pre-wrap;\">"
                    + escapeHtml(text) + "</pre>";
        }

        return RenderedEmail.builder()
                .subject(subject == null ? "" : subject)
                .preheader(preheader)
                .html(html == null ? "" : html)
                .text(text == null ? stripHtml(html) : text)
                .variablesResolved(resolved)
                .build();
    }

    private static String substitute(String input, Map<String, Object> vars) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = PLACEHOLDER.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object v = lookup(vars, key);
            String replacement = v == null ? "" : v.toString();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Object lookup(Map<String, Object> vars, String key) {
        if (vars == null) return null;
        if (!key.contains(".")) return vars.get(key);
        String[] parts = key.split("\\.");
        Object cursor = vars;
        for (String part : parts) {
            if (cursor instanceof Map<?, ?> map) {
                cursor = map.get(part);
            } else {
                return null;
            }
            if (cursor == null) return null;
        }
        return cursor;
    }

    /* ---------------------------------------------------------------- send sync */

    public EmailSendResult sendNow(EmailTemplate template,
                                   String toEmail,
                                   String toName,
                                   Map<String, Object> variables,
                                   String entityType,
                                   UUID entityId) {
        RenderedEmail rendered = render(template, variables);
        return dispatch(rendered, toEmail, toName, template.getId(), entityType, entityId);
    }

    public EmailSendResult sendRendered(RenderedEmail rendered,
                                        String toEmail,
                                        String toName,
                                        UUID templateId,
                                        String entityType,
                                        UUID entityId) {
        return dispatch(rendered, toEmail, toName, templateId, entityType, entityId);
    }

    /* --------------------------------------------------------------- send async */

    @Async
    public CompletableFuture<EmailSendResult> sendAsync(EmailTemplate template,
                                                        String toEmail,
                                                        String toName,
                                                        Map<String, Object> variables,
                                                        String entityType,
                                                        UUID entityId) {
        return CompletableFuture.completedFuture(
                sendNow(template, toEmail, toName, variables, entityType, entityId));
    }

    /* -------------------------------------------------------------- core sender */

    private EmailSendResult dispatch(RenderedEmail rendered,
                                     String toEmail,
                                     String toName,
                                     UUID templateId,
                                     String entityType,
                                     UUID entityId) {

        if (isBlank(toEmail)) {
            return failOnce("recipient email is blank", toEmail, templateId, entityType, entityId);
        }

        Throwable lastError = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                String messageId = doSend(rendered, toEmail, toName);

                Map<String, Object> details = new HashMap<>();
                details.put("recipientEmail", toEmail);
                details.put("recipientName", toName);
                details.put("subject", rendered.getSubject());
                details.put("messageId", messageId);
                details.put("attempts", attempt);
                if (templateId != null) details.put("templateId", templateId.toString());

                systemLogService.logEmail("sent", "success", entityId, entityType, details);
                log.info("Email sent to {} (attempt {}, messageId {})", toEmail, attempt, messageId);

                return EmailSendResult.builder()
                        .success(true).messageId(messageId).attempts(attempt).build();

            } catch (Exception e) {
                lastError = e;
                log.warn("Email send attempt {} of {} to {} failed: {}",
                        attempt, maxAttempts, toEmail, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Map<String, Object> details = new HashMap<>();
        details.put("recipientEmail", toEmail);
        details.put("recipientName", toName);
        details.put("subject", rendered.getSubject());
        details.put("attempts", maxAttempts);
        details.put("error", lastError == null ? "unknown" : lastError.getMessage());
        if (templateId != null) details.put("templateId", templateId.toString());

        systemLogService.logEmail("failed", "failed", entityId, entityType, details);
        log.error("Email send to {} permanently failed after {} attempts", toEmail, maxAttempts, lastError);

        return EmailSendResult.builder()
                .success(false)
                .attempts(maxAttempts)
                .error(lastError == null ? "unknown" : lastError.getMessage())
                .build();
    }

    private String doSend(RenderedEmail rendered, String toEmail, String toName)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
        if (isBlank(toName)) {
            helper.setTo(toEmail);
        } else {
            helper.setTo(new InternetAddress(toEmail, toName, StandardCharsets.UTF_8.name()));
        }
        helper.setSubject(rendered.getSubject() == null ? "" : rendered.getSubject());
        helper.setText(
                rendered.getText() == null ? "" : rendered.getText(),
                rendered.getHtml()  == null ? "" : rendered.getHtml());

        mailSender.send(mime);

        String[] header = mime.getHeader("Message-ID");
        return (header != null && header.length > 0) ? header[0] : null;
    }

    private EmailSendResult failOnce(String reason, String to, UUID templateId,
                                     String entityType, UUID entityId) {
        Map<String, Object> details = new HashMap<>();
        details.put("recipientEmail", to);
        details.put("error", reason);
        if (templateId != null) details.put("templateId", templateId.toString());
        systemLogService.logEmail("failed", "failed", entityId, entityType, details);
        return EmailSendResult.builder().success(false).error(reason).attempts(0).build();
    }

    /* ----------------------------------------------------------------- helpers */

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
