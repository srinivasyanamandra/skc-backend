package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.quote.QuoteRequestDto;
import syncqubits.ai.skc.dto.quote.QuoteResponse;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.EmailTemplate;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.EmailTemplateRepository;
import syncqubits.ai.skc.repository.QuoteRequestRepository;
import syncqubits.ai.skc.service.email.TemplateVariables;
import syncqubits.ai.skc.util.NameUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Transactional
    public QuoteResponse submitQuote(QuoteRequestDto dto) {
        log.info("Processing quote request for email: {}", dto.getEmail());

        // Find or create client with derived name
        Client client = clientRepository.findByEmail(dto.getEmail())
                .orElseGet(() -> {
                    // Derive clean name from provided name or email
                    String derivedName = NameUtils.resolveDisplayName(dto.getName(), dto.getEmail());
                    Client newClient = Client.builder()
                            .name(derivedName)
                            .email(dto.getEmail())
                            .phone(dto.getPhone())
                            .source("quote_request")
                            .status(Client.ClientStatus.LEAD)
                            .build();
                    log.info("Creating new client with derived name: {} for email: {}", derivedName, dto.getEmail());
                    return clientRepository.save(newClient);
                });

        // Create quote request
        QuoteRequest quoteRequest = QuoteRequest.builder()
                .client(client)
                .eventType(dto.getEventType())
                .eventDate(dto.getEventDate())
                .guests(dto.getGuests())
                .venue(dto.getVenue())
                .budget(dto.getBudget())
                .message(dto.getMessage())
                .status(QuoteRequest.QuoteStatus.PENDING)
                .build();

        quoteRequest = quoteRequestRepository.save(quoteRequest);

        // Log the quote request
        systemLogService.logQuote(
                "submitted",
                "success",
                quoteRequest.getId(),
                Map.of(
                        "clientEmail", dto.getEmail(),
                        "eventType", dto.getEventType(),
                        "guests", dto.getGuests()
                )
        );

        // Fire-and-forget confirmation email — never let SMTP failures break the public form
        try {
            EmailTemplate template = emailTemplateRepository
                    .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(EmailTemplate.TemplateType.QUOTE_CONFIRMATION)
                    .orElseGet(DefaultTemplates::quoteConfirmation);
            
            // Derive clean display name and first name for personalization
            String displayName = NameUtils.resolveDisplayName(client.getName(), client.getEmail());
            String firstName   = NameUtils.resolveFirstName(client.getName(), client.getEmail());

            // Canonical placeholder set + quote-specific overrides — keeps
            // every {{firstName}} / {{guests}} / {{brand}} in a template
            // resolvable, even when the quote form didn't capture every
            // detail.
            Map<String, Object> vars = TemplateVariables.liveSet();
            vars.put("clientName", displayName);
            vars.put("name",       displayName);
            vars.put("firstName",  firstName);
            vars.put("email",      client.getEmail());
            vars.put("eventType",  quoteRequest.getEventType());
            vars.put("eventDate",  quoteRequest.getEventDate().toString());
            vars.put("guests",     quoteRequest.getGuests());
            vars.put("guestCount", quoteRequest.getGuests());

            emailService.sendAsync(template, client.getEmail(), displayName,
                    vars, "quote_request", quoteRequest.getId());
        } catch (Exception e) {
            log.warn("Quote confirmation email could not be queued for {}: {}", dto.getEmail(), e.getMessage());
        }

        log.info("Quote request created with ID: {}", quoteRequest.getId());

        return QuoteResponse.builder()
                .id(quoteRequest.getId())
                .status(quoteRequest.getStatus().name().toLowerCase())
                .createdAt(quoteRequest.getCreatedAt())
                .message("Thank you. Our team will reply within 24 hours.")
                .build();
    }
}
