package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.client.AdminClientDetail;
import syncqubits.ai.skc.dto.client.AdminClientSummary;
import syncqubits.ai.skc.dto.client.ClientUpdateRequest;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.QuoteRequestRepository;
import syncqubits.ai.skc.repository.ReviewRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminClientService {

    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final ReviewRepository reviewRepository;
    private final SystemLogService systemLogService;

    public PageResponse<AdminClientSummary> list(String q,
                                                 String status,
                                                 Instant since,
                                                 Instant until,
                                                 int page,
                                                 int size,
                                                 String sortField,
                                                 Sort.Direction direction) {
        Client.ClientStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Client> result = clientRepository.searchClients(
                normalizeQ(q),
                statusEnum, since, until, PageRequest.of(page, size, sort));

        // Batch-fetch counts for all clients on this page in 2 queries instead of 2*N
        List<UUID> ids = result.getContent().stream().map(Client::getId).toList();

        Map<UUID, Long> quoteCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            quoteRequestRepository.countByClientIds(ids)
                    .forEach(row -> quoteCounts.put((UUID) row[0], (Long) row[1]));
        }

        Map<UUID, Long> reviewCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            reviewRepository.countReviewsByClientIds(ids)
                    .forEach(row -> reviewCounts.put((UUID) row[0], (Long) row[1]));
        }

        return PageResponse.from(result,
                c -> toSummary(c, quoteCounts.getOrDefault(c.getId(), 0L),
                                  reviewCounts.getOrDefault(c.getId(), 0L)));
    }

    public AdminClientDetail detail(UUID id) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client " + id + " not found"));

        List<AdminClientDetail.QuoteRef> quotes = quoteRequestRepository
                .findByClientIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(this::toQuoteRef).toList();

        List<AdminClientDetail.ReviewRef> reviews = reviewRepository
                .findReviewsByClientIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(this::toReviewRef).toList();

        return AdminClientDetail.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .source(c.getSource())
                .status(c.getStatus().name().toLowerCase())
                .notes(c.getNotes())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .quotes(quotes)
                .reviews(reviews)
                .build();
    }

    @Transactional
    public AdminClientDetail update(UUID id, ClientUpdateRequest req) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client " + id + " not found"));

        Map<String, Object> changes = new HashMap<>();

        if (req.getEmail() != null && !req.getEmail().equalsIgnoreCase(c.getEmail())) {
            clientRepository.findByEmail(req.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(c.getId())) {
                    throw new ConflictException("Another client already uses email " + req.getEmail());
                }
            });
            changes.put("email", Map.of("from", c.getEmail(), "to", req.getEmail()));
            c.setEmail(req.getEmail());
        }
        if (req.getName() != null && !req.getName().equals(c.getName())) {
            changes.put("name", Map.of("from", nullSafe(c.getName()), "to", req.getName()));
            c.setName(req.getName());
        }
        if (req.getPhone() != null && !req.getPhone().equals(c.getPhone())) {
            changes.put("phone", Map.of("from", nullSafe(c.getPhone()), "to", req.getPhone()));
            c.setPhone(req.getPhone());
        }
        if (req.getStatus() != null) {
            Client.ClientStatus newStatus = parseStatusStrict(req.getStatus());
            if (newStatus != c.getStatus()) {
                changes.put("status", Map.of(
                        "from", c.getStatus().name().toLowerCase(),
                        "to",   newStatus.name().toLowerCase()));
                c.setStatus(newStatus);
            }
        }
        if (req.getNotes() != null && !req.getNotes().equals(c.getNotes())) {
            changes.put("notes", "updated");
            c.setNotes(req.getNotes());
        }

        if (!changes.isEmpty()) {
            clientRepository.save(c);
            systemLogService.logEmail("client_updated", "success", c.getId(), "client", changes);
        }

        return detail(id);
    }

    /* ---------------------------------------------------------------- helpers */

    private AdminClientSummary toSummary(Client c, long quoteCount, long reviewCount) {
        return AdminClientSummary.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .source(c.getSource())
                .status(c.getStatus().name().toLowerCase())
                .quoteCount(quoteCount)
                .reviewCount(reviewCount)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private AdminClientDetail.QuoteRef toQuoteRef(QuoteRequest q) {
        return AdminClientDetail.QuoteRef.builder()
                .id(q.getId())
                .eventType(q.getEventType())
                .eventDate(q.getEventDate())
                .guests(q.getGuests())
                .status(q.getStatus().name().toLowerCase())
                .createdAt(q.getCreatedAt())
                .build();
    }

    private AdminClientDetail.ReviewRef toReviewRef(Review r) {
        return AdminClientDetail.ReviewRef.builder()
                .id(r.getId())
                .type(r.getType().name().toLowerCase())
                .overallRating(r.getOverallRating())
                .status(r.getStatus() == null ? null : r.getStatus().name().toLowerCase())
                .isFeatured(r.getIsFeatured())
                .submittedAt(r.getSubmittedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private Client.ClientStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }

    private Client.ClientStatus parseStatusStrict(String raw) {
        try {
            return Client.ClientStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw +
                    "'. Allowed: lead, contacted, quoted, booked, completed, cancelled.");
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /** Normalize free-text search to "" (matches everything via LIKE '%%') so the
     *  JPQL parameter always has a definite varchar type for PostgreSQL. */
    static String normalizeQ(String q) { return (q == null) ? "" : q.trim(); }
}
