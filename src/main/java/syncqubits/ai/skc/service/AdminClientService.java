package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.client.AdminClientDetail;
import syncqubits.ai.skc.dto.client.AdminClientSummary;
import syncqubits.ai.skc.dto.client.ClientAddressRequest;
import syncqubits.ai.skc.dto.client.ClientAddressResponse;
import syncqubits.ai.skc.dto.client.ClientCreateRequest;
import syncqubits.ai.skc.dto.client.ClientNoteRequest;
import syncqubits.ai.skc.dto.client.ClientNoteResponse;
import syncqubits.ai.skc.dto.client.ClientUpdateRequest;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.entity.embedded.AddressDoc;
import syncqubits.ai.skc.entity.embedded.NoteDoc;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BookingRepository;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.QuoteRequestRepository;
import syncqubits.ai.skc.repository.ReviewRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRM service. After the data-model consolidation this works directly on
 * the {@link Client} entity's embedded JSONB collections — addresses,
 * notesLog — and the TEXT[] tags column. There are no per-element child
 * tables; "add a note" mutates the in-memory list and {@code save()}s the
 * client, which Hibernate flushes back to JSONB.
 *
 * Invariants preserved by this service (no DB-level constraints):
 *   - At most one address per client has {@code primary=true}.
 *   - Deleting the primary address auto-promotes the next-oldest.
 *   - Tag values are stored case-insensitively (lowercase).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminClientService {

    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
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

        List<UUID> ids = result.getContent().stream().map(Client::getId).toList();
        Map<UUID, Long> quoteCounts   = countMap(ids, quoteRequestRepository::countByClientIds);
        Map<UUID, Long> reviewCounts  = countMap(ids, reviewRepository::countReviewsByClientIds);
        Map<UUID, Long> bookingCounts = countMap(ids, bookingRepository::countByClientIds);

        return PageResponse.from(result, c -> toSummary(c,
                quoteCounts.getOrDefault(c.getId(), 0L),
                reviewCounts.getOrDefault(c.getId(), 0L),
                bookingCounts.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public AdminClientDetail detail(UUID id) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client " + id + " not found"));

        List<AdminClientDetail.QuoteRef> quotes = quoteRequestRepository
                .findByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(this::toQuoteRef).toList();

        List<AdminClientDetail.ReviewRef> reviews = reviewRepository
                .findReviewsByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(this::toReviewRef).toList();

        List<AdminClientDetail.BookingRef> bookings = bookingRepository
                .findByClientIdOrderByEventDateDesc(c.getId()).stream().map(this::toBookingRef).toList();

        // Notes are pinned-first then newest-first (ordering is a UI concern,
        // applied here so the response is render-ready).
        List<ClientNoteResponse> notes = c.getNotesLog().stream()
                .sorted(Comparator
                        .comparing((NoteDoc n) -> Boolean.TRUE.equals(n.getPinned()) ? 0 : 1)
                        .thenComparing(NoteDoc::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toNoteResponse).toList();

        // Primary address first.
        List<ClientAddressResponse> addresses = c.getAddresses().stream()
                .sorted(Comparator
                        .comparing((AddressDoc a) -> Boolean.TRUE.equals(a.getPrimary()) ? 0 : 1)
                        .thenComparing(AddressDoc::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAddressResponse).toList();

        return AdminClientDetail.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .source(c.getSource())
                .status(c.getStatus().name().toLowerCase())
                .notes(c.getNotes())
                .companyName(c.getCompanyName())
                .lifecycleStage(c.getLifecycleStage() == null ? null : c.getLifecycleStage().name().toLowerCase())
                .referralSource(c.getReferralSource())
                .lifetimeValueCents(c.getLifetimeValueCents())
                .lastContactedAt(c.getLastContactedAt())
                .preferredContact(c.getPreferredContact())
                .dietaryNotes(c.getDietaryNotes())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .quotes(quotes)
                .reviews(reviews)
                .bookings(bookings)
                .timelineNotes(notes)
                .addresses(addresses)
                .tags(new LinkedHashSet<>(c.getTags()))
                .build();
    }

    /* ──────────────────────────────────── client mutation ───────────────────────── */

    @Transactional
    public AdminClientDetail create(ClientCreateRequest req) {
        clientRepository.findByEmail(req.getEmail()).ifPresent(existing -> {
            throw new ConflictException("A client with email " + req.getEmail() + " already exists.");
        });

        Client.ClientStatus status = req.getStatus() == null || req.getStatus().isBlank()
                ? Client.ClientStatus.LEAD
                : parseStatusStrict(req.getStatus());
        Client.LifecycleStage lifecycle = req.getLifecycleStage() == null || req.getLifecycleStage().isBlank()
                ? Client.LifecycleStage.PROSPECT
                : parseLifecycle(req.getLifecycleStage());

        Client c = Client.builder()
                .name(req.getName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .phone(req.getPhone().trim())
                .source(req.getSource() == null || req.getSource().isBlank() ? "manual" : req.getSource())
                .status(status)
                .lifecycleStage(lifecycle)
                .companyName(req.getCompanyName())
                .referralSource(req.getReferralSource())
                .preferredContact(req.getPreferredContact())
                .notes(req.getNotes())
                .build();
        Client saved = clientRepository.save(c);

        systemLogService.logEmail("client_created", "success", saved.getId(), "client",
                Map.of("source", saved.getSource(), "email", saved.getEmail()));

        return detail(saved.getId());
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

        if (req.getCompanyName() != null) c.setCompanyName(req.getCompanyName());
        if (req.getLifecycleStage() != null) {
            Client.LifecycleStage stage = parseLifecycle(req.getLifecycleStage());
            if (stage != c.getLifecycleStage()) {
                changes.put("lifecycleStage", Map.of(
                        "from", c.getLifecycleStage() == null ? null : c.getLifecycleStage().name().toLowerCase(),
                        "to",   stage.name().toLowerCase()));
                c.setLifecycleStage(stage);
            }
        }
        if (req.getReferralSource() != null)     c.setReferralSource(req.getReferralSource());
        if (req.getLifetimeValueCents() != null) c.setLifetimeValueCents(req.getLifetimeValueCents());
        if (req.getPreferredContact() != null)   c.setPreferredContact(req.getPreferredContact());
        if (req.getDietaryNotes() != null)       c.setDietaryNotes(req.getDietaryNotes());

        if (!changes.isEmpty()) {
            clientRepository.save(c);
            systemLogService.logEmail("client_updated", "success", c.getId(), "client", changes);
        }
        return detail(id);
    }

    @Transactional
    public AdminClientDetail touchLastContacted(UUID id) {
        Client c = mustFind(id);
        c.setLastContactedAt(Instant.now());
        clientRepository.save(c);
        return detail(id);
    }

    /* ──────────────────────────────────── notes (notes_log JSONB) ───────────────── */

    @Transactional
    public ClientNoteResponse addNote(UUID clientId, ClientNoteRequest req) {
        Client c = mustFind(clientId);

        NoteDoc note = NoteDoc.builder()
                .id(UUID.randomUUID())
                .body(req.getBody())
                .category(req.getCategory())
                .pinned(Boolean.TRUE.equals(req.getPinned()))
                .authorEmail(currentAdminEmail())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        c.getNotesLog().add(note);
        c.setLastContactedAt(Instant.now());
        clientRepository.save(c);
        return toNoteResponse(note);
    }

    @Transactional
    public ClientNoteResponse updateNote(UUID clientId, UUID noteId, ClientNoteRequest req) {
        Client c = mustFind(clientId);
        NoteDoc note = c.getNotesLog().stream()
                .filter(n -> noteId.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Note " + noteId + " not found"));

        if (req.getBody() != null)     note.setBody(req.getBody());
        if (req.getCategory() != null) note.setCategory(req.getCategory());
        if (req.getPinned() != null)   note.setPinned(req.getPinned());
        note.setUpdatedAt(Instant.now());
        clientRepository.save(c);
        return toNoteResponse(note);
    }

    @Transactional
    public void deleteNote(UUID clientId, UUID noteId) {
        Client c = mustFind(clientId);
        boolean removed = c.getNotesLog().removeIf(n -> noteId.equals(n.getId()));
        if (!removed) {
            throw new ResourceNotFoundException("Note " + noteId + " not found");
        }
        clientRepository.save(c);
    }

    /* ──────────────────────────────────── addresses (JSONB) ─────────────────────── */

    @Transactional
    public ClientAddressResponse addAddress(UUID clientId, ClientAddressRequest req) {
        Client c = mustFind(clientId);

        boolean shouldBePrimary = Boolean.TRUE.equals(req.getPrimary())
                || c.getAddresses().stream().noneMatch(a -> Boolean.TRUE.equals(a.getPrimary()));

        if (shouldBePrimary) {
            c.getAddresses().forEach(a -> a.setPrimary(false));
        }

        AddressDoc addr = AddressDoc.builder()
                .id(UUID.randomUUID())
                .label(req.getLabel() == null || req.getLabel().isBlank() ? "home" : req.getLabel())
                .line1(req.getLine1())
                .line2(req.getLine2())
                .city(req.getCity())
                .state(req.getState())
                .pincode(req.getPincode())
                .primary(shouldBePrimary)
                .createdAt(Instant.now())
                .build();

        c.getAddresses().add(addr);
        clientRepository.save(c);
        return toAddressResponse(addr);
    }

    @Transactional
    public ClientAddressResponse updateAddress(UUID clientId, UUID addressId, ClientAddressRequest req) {
        Client c = mustFind(clientId);
        AddressDoc addr = c.getAddresses().stream()
                .filter(a -> addressId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address " + addressId + " not found"));

        if (req.getLabel()   != null) addr.setLabel(req.getLabel());
        if (req.getLine1()   != null) addr.setLine1(req.getLine1());
        if (req.getLine2()   != null) addr.setLine2(req.getLine2());
        if (req.getCity()    != null) addr.setCity(req.getCity());
        if (req.getState()   != null) addr.setState(req.getState());
        if (req.getPincode() != null) addr.setPincode(req.getPincode());

        if (Boolean.TRUE.equals(req.getPrimary()) && !Boolean.TRUE.equals(addr.getPrimary())) {
            c.getAddresses().forEach(a -> a.setPrimary(addressId.equals(a.getId())));
        }

        clientRepository.save(c);
        return toAddressResponse(addr);
    }

    @Transactional
    public void deleteAddress(UUID clientId, UUID addressId) {
        Client c = mustFind(clientId);
        AddressDoc removed = c.getAddresses().stream()
                .filter(a -> addressId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address " + addressId + " not found"));

        c.getAddresses().removeIf(a -> addressId.equals(a.getId()));

        // Promote the next-oldest address if we just deleted the primary,
        // so the client always has a primary while at least one address
        // remains.
        if (Boolean.TRUE.equals(removed.getPrimary()) && !c.getAddresses().isEmpty()) {
            AddressDoc next = c.getAddresses().stream()
                    .min(Comparator.comparing(AddressDoc::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(c.getAddresses().get(0));
            next.setPrimary(true);
        }

        clientRepository.save(c);
    }

    /* ──────────────────────────────────── tags (TEXT[]) ─────────────────────────── */

    /**
     * Replace the entire tag set on a client. Tags are stored lowercase
     * to keep the namespace flat ("Premium" and "premium" collapse to
     * the same tag).
     */
    @Transactional
    public Set<String> setClientTags(UUID clientId, List<String> tags) {
        Client c = mustFind(clientId);
        Set<String> normalized = normalizeTags(tags);
        c.setTags(normalized);
        clientRepository.save(c);
        return new LinkedHashSet<>(normalized);
    }

    /**
     * Distinct tags currently in use across all clients. Drives the
     * tag-picker autocomplete on the client drawer.
     */
    @Transactional(readOnly = true)
    public List<String> distinctClientTags() {
        return clientRepository.distinctTags();
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    @FunctionalInterface
    private interface BatchCounter {
        List<Object[]> call(java.util.Collection<UUID> ids);
    }

    private static Map<UUID, Long> countMap(List<UUID> ids, BatchCounter counter) {
        Map<UUID, Long> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        counter.call(ids).forEach(row -> out.put((UUID) row[0], (Long) row[1]));
        return out;
    }

    private Client mustFind(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client " + id + " not found"));
    }

    private static Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return new LinkedHashSet<>();
        return tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /* ── mappers ── */

    private AdminClientSummary toSummary(Client c, long quoteCount, long reviewCount, long bookingCount) {
        return AdminClientSummary.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .source(c.getSource())
                .status(c.getStatus().name().toLowerCase())
                .lifecycleStage(c.getLifecycleStage() == null ? null : c.getLifecycleStage().name().toLowerCase())
                .companyName(c.getCompanyName())
                .quoteCount(quoteCount)
                .reviewCount(reviewCount)
                .bookingCount(bookingCount)
                .lifetimeValueCents(c.getLifetimeValueCents())
                .lastContactedAt(c.getLastContactedAt())
                .tags(new LinkedHashSet<>(c.getTags()))
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

    private AdminClientDetail.BookingRef toBookingRef(Booking b) {
        return AdminClientDetail.BookingRef.builder()
                .id(b.getId())
                .reference(b.getReference())
                .eventType(b.getEventType())
                .eventDate(b.getEventDate())
                .guestCount(b.getGuestCount())
                .status(b.getStatus().name().toLowerCase())
                .totalAmountCents(b.getTotalAmountCents())
                .paidAmountCents(b.getPaidAmountCents())
                .currency(b.getCurrency())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private ClientNoteResponse toNoteResponse(NoteDoc n) {
        return ClientNoteResponse.builder()
                .id(n.getId())
                .body(n.getBody())
                .category(n.getCategory())
                .pinned(n.getPinned())
                .authorEmail(n.getAuthorEmail())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    private ClientAddressResponse toAddressResponse(AddressDoc a) {
        return ClientAddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .line1(a.getLine1())
                .line2(a.getLine2())
                .city(a.getCity())
                .state(a.getState())
                .pincode(a.getPincode())
                .primary(a.getPrimary())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getCreatedAt())
                .build();
    }

    /* ── parsers ── */

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

    private Client.LifecycleStage parseLifecycle(String raw) {
        try {
            return Client.LifecycleStage.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid lifecycle stage '" + raw +
                    "'. Allowed: prospect, active, repeat, vip, dormant, archived.");
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String currentAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "system";
        Object principal = auth.getPrincipal();
        return principal == null ? "system" : principal.toString();
    }

    static String normalizeQ(String q) { return (q == null) ? "" : q.trim(); }
}
