package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.quote.AdminQuoteDetail;
import syncqubits.ai.skc.dto.quote.AdminQuoteSummary;
import syncqubits.ai.skc.dto.quote.QuoteStatusUpdateRequest;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.SystemLog;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.QuoteRequestRepository;
import syncqubits.ai.skc.repository.SystemLogRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminQuoteService {

    private final QuoteRequestRepository quoteRequestRepository;
    private final SystemLogRepository systemLogRepository;
    private final SystemLogService systemLogService;

    @Transactional(readOnly = true)
    public PageResponse<AdminQuoteSummary> list(String status,
                                                String eventType,
                                                LocalDate fromDate,
                                                LocalDate toDate,
                                                int page,
                                                int size,
                                                String sortField,
                                                Sort.Direction direction) {
        QuoteRequest.QuoteStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);
        Page<QuoteRequest> result = quoteRequestRepository.searchQuotes(
                statusEnum, eventType == null ? "" : eventType.trim(),
                fromDate, toDate, PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminQuoteDetail detail(UUID id) {
        QuoteRequest q = quoteRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + id + " not found"));
        Client c = q.getClient();
        List<SystemLog> logs = systemLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("quote_request", q.getId());

        return AdminQuoteDetail.builder()
                .id(q.getId())
                .eventType(q.getEventType())
                .eventDate(q.getEventDate())
                .guests(q.getGuests())
                .venue(q.getVenue())
                .budget(q.getBudget())
                .message(q.getMessage())
                .status(q.getStatus().name().toLowerCase())
                .respondedAt(q.getRespondedAt())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .client(AdminQuoteDetail.Client.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .email(c.getEmail())
                        .phone(c.getPhone())
                        .status(c.getStatus().name().toLowerCase())
                        .source(c.getSource())
                        .build())
                .activity(logs.stream().map(this::logToMap).toList())
                .build();
    }

    @Transactional
    public AdminQuoteDetail updateStatus(UUID id, QuoteStatusUpdateRequest req) {
        QuoteRequest q = quoteRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + id + " not found"));

        QuoteRequest.QuoteStatus newStatus = parseStatusStrict(req.getStatus());
        QuoteRequest.QuoteStatus prev = q.getStatus();
        if (prev == newStatus) {
            return detail(id);
        }

        q.setStatus(newStatus);
        if (newStatus != QuoteRequest.QuoteStatus.PENDING && q.getRespondedAt() == null) {
            q.setRespondedAt(Instant.now());
        }
        quoteRequestRepository.save(q);

        Map<String, Object> details = new HashMap<>();
        details.put("from", prev.name().toLowerCase());
        details.put("to", newStatus.name().toLowerCase());
        if (req.getNote() != null && !req.getNote().isBlank()) details.put("note", req.getNote());
        systemLogService.logQuote("status_updated", "success", q.getId(), details);

        return detail(id);
    }

    /* ---------------------------------------------------------------- helpers */

    private AdminQuoteSummary toSummary(QuoteRequest q) {
        Client c = q.getClient();
        return AdminQuoteSummary.builder()
                .id(q.getId())
                .clientId(c.getId())
                .clientName(c.getName())
                .clientEmail(c.getEmail())
                .clientPhone(c.getPhone())
                .eventType(q.getEventType())
                .eventDate(q.getEventDate())
                .guests(q.getGuests())
                .venue(q.getVenue())
                .budget(q.getBudget())
                .status(q.getStatus().name().toLowerCase())
                .respondedAt(q.getRespondedAt())
                .createdAt(q.getCreatedAt())
                .build();
    }

    private Map<String, Object> logToMap(SystemLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId().toString());
        m.put("type", l.getType());
        m.put("action", l.getAction());
        m.put("status", l.getStatus());
        m.put("at", l.getCreatedAt().toString());
        if (l.getDetails() != null) m.put("details", l.getDetails());
        return m;
    }

    private QuoteRequest.QuoteStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }

    private QuoteRequest.QuoteStatus parseStatusStrict(String raw) {
        try {
            return QuoteRequest.QuoteStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw + "'. Allowed: pending, contacted, quoted, booked, declined.");
        }
    }
}
