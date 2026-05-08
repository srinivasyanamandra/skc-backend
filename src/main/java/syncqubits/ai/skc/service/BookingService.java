package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.booking.AdminBookingDetail;
import syncqubits.ai.skc.dto.booking.AdminBookingSummary;
import syncqubits.ai.skc.dto.booking.BookingCreateRequest;
import syncqubits.ai.skc.dto.booking.BookingTaskRequest;
import syncqubits.ai.skc.dto.booking.BookingUpdateRequest;
import syncqubits.ai.skc.dto.booking.ConvertQuoteRequest;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.Invoice;
import syncqubits.ai.skc.entity.PurchaseOrder;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.embedded.TaskDoc;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BookingRepository;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.InvoiceRepository;
import syncqubits.ai.skc.repository.PurchaseOrderRepository;
import syncqubits.ai.skc.repository.QuoteRequestRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Booking lifecycle service. After the data-model consolidation, the
 * checklist that used to live in {@code booking_tasks} is now a JSONB
 * array on the booking row — same operations, simpler shape.
 *
 * Responsibilities:
 *   1. List + detail with joins the admin UI needs (no N+1).
 *   2. Booking mutation — manual create, partial update, status, and the
 *      central "convert quote → booking" flow.
 *   3. Embedded checklist (tasks JSONB) CRUD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<AdminBookingSummary> list(String q,
                                                  String status,
                                                  String eventType,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  int page,
                                                  int size,
                                                  String sortField,
                                                  Sort.Direction direction) {
        Booking.BookingStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Booking> result = bookingRepository.searchBookings(
                q == null ? "" : q.trim(),
                statusEnum,
                eventType == null ? "" : eventType.trim(),
                fromDate, toDate,
                PageRequest.of(page, size, sort));

        return PageResponse.from(result, b -> toSummary(b, openTaskCount(b)));
    }

    @Transactional(readOnly = true)
    public AdminBookingDetail detail(UUID id) {
        Booking b = mustFind(id);
        return toDetail(b);
    }

    /* ──────────────────────────────────── mutations ─────────────────────────────── */

    @Transactional
    public AdminBookingDetail create(BookingCreateRequest req) {
        Client client = clientRepository.findById(req.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client " + req.getClientId() + " not found"));

        QuoteRequest quote = null;
        if (req.getQuoteRequestId() != null) {
            quote = quoteRequestRepository.findById(req.getQuoteRequestId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Quote " + req.getQuoteRequestId() + " not found"));
            if (bookingRepository.existsByQuoteRequestId(quote.getId())) {
                throw new ConflictException(
                        "Quote " + quote.getId() + " is already linked to a booking.");
            }
            if (!quote.getClient().getId().equals(client.getId())) {
                throw new BadRequestException(
                        "Quote does not belong to the supplied client.");
            }
        }

        Booking b = Booking.builder()
                .reference(allocateReference())
                .client(client)
                .quoteRequest(quote)
                .eventType(req.getEventType())
                .eventDate(req.getEventDate())
                .eventStartTime(req.getEventStartTime())
                .eventEndTime(req.getEventEndTime())
                .guestCount(req.getGuestCount())
                .venueName(req.getVenueName())
                .venueAddress(req.getVenueAddress())
                .serviceStyle(req.getServiceStyle())
                .packageName(req.getPackageName())
                .totalAmountCents(req.getTotalAmountCents() == null ? 0L : req.getTotalAmountCents())
                .depositAmountCents(req.getDepositAmountCents() == null ? 0L : req.getDepositAmountCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? "INR" : req.getCurrency())
                .internalNotes(req.getInternalNotes())
                .clientNotes(req.getClientNotes())
                .build();

        Booking saved = bookingRepository.save(b);
        promoteClientToBookedIfEarlier(client);

        systemLogService.logQuote("booking_created", "success", saved.getId(),
                Map.of("reference", saved.getReference(),
                       "client", client.getId().toString(),
                       "guests", saved.getGuestCount()));

        return toDetail(saved);
    }

    @Transactional
    public AdminBookingDetail convertFromQuote(UUID quoteId, ConvertQuoteRequest req) {
        QuoteRequest quote = quoteRequestRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + quoteId + " not found"));

        if (bookingRepository.existsByQuoteRequestId(quoteId)) {
            throw new ConflictException("Quote " + quoteId + " is already linked to a booking.");
        }

        Client client = quote.getClient();

        Booking b = Booking.builder()
                .reference(allocateReference())
                .client(client)
                .quoteRequest(quote)
                .eventType(quote.getEventType())
                .eventDate(quote.getEventDate())
                .eventStartTime(req.getEventStartTime())
                .eventEndTime(req.getEventEndTime())
                .guestCount(quote.getGuests())
                .venueName(quote.getVenue())
                .serviceStyle(req.getServiceStyle())
                .packageName(req.getPackageName())
                .totalAmountCents(req.getTotalAmountCents() == null ? 0L : req.getTotalAmountCents())
                .depositAmountCents(req.getDepositAmountCents() == null ? 0L : req.getDepositAmountCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? "INR" : req.getCurrency())
                .internalNotes(req.getInternalNotes())
                .clientNotes(quote.getMessage())
                .build();

        if (Boolean.TRUE.equals(req.getSeedDefaultTasks()) || req.getSeedDefaultTasks() == null) {
            seedDefaultTasks(b);
        }

        Booking saved = bookingRepository.save(b);

        if (quote.getStatus() != QuoteRequest.QuoteStatus.BOOKED) {
            quote.setStatus(QuoteRequest.QuoteStatus.BOOKED);
            if (quote.getRespondedAt() == null) quote.setRespondedAt(Instant.now());
            quoteRequestRepository.save(quote);
        }

        promoteClientToBookedIfEarlier(client);

        systemLogService.logQuote("booking_converted", "success", saved.getId(),
                Map.of("reference", saved.getReference(),
                       "fromQuote", quoteId.toString()));

        return toDetail(saved);
    }

    @Transactional
    public AdminBookingDetail update(UUID id, BookingUpdateRequest req) {
        Booking b = mustFind(id);
        Map<String, Object> changes = new HashMap<>();

        if (req.getEventType() != null) { changes.put("eventType", req.getEventType()); b.setEventType(req.getEventType()); }
        if (req.getEventDate() != null) { changes.put("eventDate", req.getEventDate().toString()); b.setEventDate(req.getEventDate()); }
        if (req.getEventStartTime() != null) b.setEventStartTime(req.getEventStartTime());
        if (req.getEventEndTime()   != null) b.setEventEndTime(req.getEventEndTime());
        if (req.getGuestCount() != null) {
            if (req.getGuestCount() < 1) throw new BadRequestException("guestCount must be ≥ 1");
            b.setGuestCount(req.getGuestCount());
        }
        if (req.getVenueName()    != null) b.setVenueName(req.getVenueName());
        if (req.getVenueAddress() != null) b.setVenueAddress(req.getVenueAddress());
        if (req.getServiceStyle() != null) b.setServiceStyle(req.getServiceStyle());
        if (req.getPackageName()  != null) b.setPackageName(req.getPackageName());

        Booking.BookingStatus prevStatus = b.getStatus();
        if (req.getStatus() != null) {
            Booking.BookingStatus newStatus = parseStatusStrict(req.getStatus());
            if (newStatus != prevStatus) {
                changes.put("status", Map.of(
                        "from", prevStatus.name().toLowerCase(),
                        "to",   newStatus.name().toLowerCase()));
                b.setStatus(newStatus);
            }
        }

        if (req.getTotalAmountCents()   != null) b.setTotalAmountCents(req.getTotalAmountCents());
        if (req.getDepositAmountCents() != null) b.setDepositAmountCents(req.getDepositAmountCents());
        if (req.getPaidAmountCents()    != null) b.setPaidAmountCents(req.getPaidAmountCents());
        if (req.getCurrency()           != null && !req.getCurrency().isBlank()) b.setCurrency(req.getCurrency());

        if (req.getInternalNotes() != null) b.setInternalNotes(req.getInternalNotes());
        if (req.getClientNotes()   != null) b.setClientNotes(req.getClientNotes());

        Booking saved = bookingRepository.save(b);

        // Lifetime-value rollup when a booking moves to COMPLETED.
        if (b.getStatus() == Booking.BookingStatus.COMPLETED && prevStatus != Booking.BookingStatus.COMPLETED) {
            Client c = b.getClient();
            long current = c.getLifetimeValueCents() == null ? 0L : c.getLifetimeValueCents();
            c.setLifetimeValueCents(current + (b.getPaidAmountCents() == null ? 0L : b.getPaidAmountCents()));
            c.setStatus(Client.ClientStatus.COMPLETED);
            if (c.getLifecycleStage() == Client.LifecycleStage.PROSPECT
                    || c.getLifecycleStage() == Client.LifecycleStage.ACTIVE) {
                c.setLifecycleStage(Client.LifecycleStage.REPEAT);
            }
            clientRepository.save(c);
        }

        if (!changes.isEmpty()) {
            systemLogService.logQuote("booking_updated", "success", saved.getId(), changes);
        }
        return toDetail(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Booking " + id + " not found");
        }
        bookingRepository.deleteById(id);
        systemLogService.logQuote("booking_deleted", "success", id, Map.of());
    }

    /* ──────────────────────────────────── tasks (JSONB) ─────────────────────────── */

    @Transactional
    public AdminBookingDetail.Task addTask(UUID bookingId, BookingTaskRequest req) {
        Booking b = mustFind(bookingId);
        TaskDoc t = TaskDoc.builder()
                .id(UUID.randomUUID())
                .title(req.getTitle())
                .description(req.getDescription())
                .dueAt(req.getDueAt())
                .assignee(req.getAssignee())
                .status(req.getStatus() == null ? "todo" : normalizeTaskStatus(req.getStatus()))
                .position(req.getPosition() == null ? b.getTasks().size() : req.getPosition())
                .createdAt(Instant.now())
                .build();
        b.getTasks().add(t);
        bookingRepository.save(b);
        return toTaskDto(t);
    }

    @Transactional
    public AdminBookingDetail.Task updateTask(UUID bookingId, UUID taskId, BookingTaskRequest req) {
        Booking b = mustFind(bookingId);
        TaskDoc t = b.getTasks().stream()
                .filter(x -> taskId.equals(x.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task " + taskId + " not found"));

        if (req.getTitle()       != null) t.setTitle(req.getTitle());
        if (req.getDescription() != null) t.setDescription(req.getDescription());
        if (req.getDueAt()       != null) t.setDueAt(req.getDueAt());
        if (req.getAssignee()    != null) t.setAssignee(req.getAssignee());
        if (req.getPosition()    != null) t.setPosition(req.getPosition());
        if (req.getStatus()      != null) {
            String next = normalizeTaskStatus(req.getStatus());
            if ("done".equals(next) && !"done".equals(t.getStatus())) {
                t.setCompletedAt(Instant.now());
            } else if (!"done".equals(next)) {
                t.setCompletedAt(null);
            }
            t.setStatus(next);
        }
        bookingRepository.save(b);
        return toTaskDto(t);
    }

    @Transactional
    public void deleteTask(UUID bookingId, UUID taskId) {
        Booking b = mustFind(bookingId);
        boolean removed = b.getTasks().removeIf(t -> taskId.equals(t.getId()));
        if (!removed) throw new ResourceNotFoundException("Task " + taskId + " not found");
        bookingRepository.save(b);
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    private Booking mustFind(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + id + " not found"));
    }

    private long openTaskCount(Booking b) {
        if (b.getTasks() == null) return 0L;
        return b.getTasks().stream().filter(t -> !"done".equalsIgnoreCase(t.getStatus())).count();
    }

    private String allocateReference() {
        long n = bookingRepository.nextReferenceNumber();
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        return String.format("SKC-%d-%06d", year, n);
    }

    private void seedDefaultTasks(Booking b) {
        String[][] defaults = new String[][] {
            {"Confirm venue access window", "Coordinate with venue manager for setup/teardown timings."},
            {"Lock final headcount with client", "Confirm guest count 7 days before event."},
            {"Sign off menu",                 "Get client approval on dishes, dietary swaps, and counters."},
            {"Plan staff roster",             "Assign chefs, servers, captain. Share with team 3 days prior."},
            {"Logistics & transport",         "Schedule equipment, raw material, and team transport."},
            {"Day-of briefing",               "Pre-event huddle 90 min before service."},
        };
        Instant now = Instant.now();
        int pos = 0;
        for (String[] row : defaults) {
            b.getTasks().add(TaskDoc.builder()
                    .id(UUID.randomUUID())
                    .title(row[0])
                    .description(row[1])
                    .status("todo")
                    .position(pos++)
                    .createdAt(now)
                    .build());
        }
    }

    private void promoteClientToBookedIfEarlier(Client c) {
        Set<Client.ClientStatus> earlier = new HashSet<>(List.of(
                Client.ClientStatus.LEAD, Client.ClientStatus.CONTACTED, Client.ClientStatus.QUOTED));
        if (earlier.contains(c.getStatus())) {
            c.setStatus(Client.ClientStatus.BOOKED);
        }
        if (c.getLifecycleStage() == Client.LifecycleStage.PROSPECT) {
            c.setLifecycleStage(Client.LifecycleStage.ACTIVE);
        }
        clientRepository.save(c);
    }

    private AdminBookingSummary toSummary(Booking b, long openTaskCount) {
        Client c = b.getClient();
        return AdminBookingSummary.builder()
                .id(b.getId())
                .reference(b.getReference())
                .clientId(c.getId())
                .clientName(c.getName())
                .clientEmail(c.getEmail())
                .clientPhone(c.getPhone())
                .eventType(b.getEventType())
                .eventDate(b.getEventDate())
                .guestCount(b.getGuestCount())
                .venueName(b.getVenueName())
                .packageName(b.getPackageName())
                .status(b.getStatus().name().toLowerCase())
                .totalAmountCents(b.getTotalAmountCents())
                .paidAmountCents(b.getPaidAmountCents())
                .currency(b.getCurrency())
                .openTaskCount(openTaskCount)
                .createdAt(b.getCreatedAt())
                .build();
    }

    private AdminBookingDetail toDetail(Booking b) {
        Client c = b.getClient();
        List<AdminBookingDetail.Task> tasks = b.getTasks().stream()
                .sorted(Comparator.comparing(TaskDoc::getPosition,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskDoc::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTaskDto).toList();
        List<PurchaseOrder> bookingPos = purchaseOrderRepository.findByBookingId(b.getId());
        List<AdminBookingDetail.PoRef> pos = bookingPos.stream().map(this::toPoRef).toList();
        List<Invoice> bookingInvoices = invoiceRepository.findByBookingId(b.getId());

        AdminBookingDetail.FinanceSummary finance = computeFinanceSummary(b, bookingPos, bookingInvoices);

        return AdminBookingDetail.builder()
                .id(b.getId())
                .reference(b.getReference())
                .client(AdminBookingDetail.Client.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .email(c.getEmail())
                        .phone(c.getPhone())
                        .status(c.getStatus().name().toLowerCase())
                        .lifecycleStage(c.getLifecycleStage() == null ? null
                                : c.getLifecycleStage().name().toLowerCase())
                        .build())
                .quoteRequestId(b.getQuoteRequest() == null ? null : b.getQuoteRequest().getId())
                .eventType(b.getEventType())
                .eventDate(b.getEventDate())
                .eventStartTime(b.getEventStartTime())
                .eventEndTime(b.getEventEndTime())
                .guestCount(b.getGuestCount())
                .venueName(b.getVenueName())
                .venueAddress(b.getVenueAddress())
                .serviceStyle(b.getServiceStyle())
                .packageName(b.getPackageName())
                .status(b.getStatus().name().toLowerCase())
                .totalAmountCents(b.getTotalAmountCents())
                .depositAmountCents(b.getDepositAmountCents())
                .paidAmountCents(b.getPaidAmountCents())
                .invoicedAmountCents(b.getInvoicedAmountCents())
                .directExpenseCents(b.getDirectExpenseCents())
                .currency(b.getCurrency())
                .finance(finance)
                .internalNotes(b.getInternalNotes())
                .clientNotes(b.getClientNotes())
                .menuSummary(b.getMenuSummary())
                .staffing(b.getStaffing())
                .tasks(tasks)
                .purchaseOrders(pos)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private AdminBookingDetail.Task toTaskDto(TaskDoc t) {
        return AdminBookingDetail.Task.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .dueAt(t.getDueAt())
                .assignee(t.getAssignee())
                .status(t.getStatus())
                .position(t.getPosition())
                .completedAt(t.getCompletedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private AdminBookingDetail.FinanceSummary computeFinanceSummary(Booking b,
                                                                    List<PurchaseOrder> pos,
                                                                    List<Invoice> invoices) {
        long invoiced = invoices.stream()
                .filter(i -> i.getStatus() != Invoice.InvoiceStatus.VOID)
                .mapToLong(i -> i.getTotalCents() == null ? 0L : i.getTotalCents()).sum();
        long invoicePaid = invoices.stream()
                .filter(i -> i.getStatus() != Invoice.InvoiceStatus.VOID)
                .mapToLong(i -> i.getPaidCents() == null ? 0L : i.getPaidCents()).sum();
        long bookingPaid = b.getPaidAmountCents() == null ? 0L : b.getPaidAmountCents();
        long advanceUnallocated = Math.max(0, bookingPaid - invoicePaid);

        long vendorCommitted = pos.stream()
                .filter(p -> p.getStatus() != PurchaseOrder.PoStatus.CANCELLED)
                .mapToLong(p -> p.getTotalCents() == null ? 0L : p.getTotalCents()).sum();
        long vendorPaid = pos.stream()
                .filter(p -> p.getStatus() != PurchaseOrder.PoStatus.CANCELLED)
                .mapToLong(p -> p.getPaidCents() == null ? 0L : p.getPaidCents()).sum();

        long directExpense = b.getDirectExpenseCents() == null ? 0L : b.getDirectExpenseCents();
        long totalCost = vendorPaid + directExpense;
        long margin    = bookingPaid - totalCost;

        List<AdminBookingDetail.InvoiceRef> invoiceRefs = invoices.stream()
                .map(i -> AdminBookingDetail.InvoiceRef.builder()
                        .id(i.getId())
                        .reference(i.getReference())
                        .status(i.getStatus().name().toLowerCase())
                        .issueDate(i.getIssueDate())
                        .dueDate(i.getDueDate())
                        .totalCents(i.getTotalCents())
                        .paidCents(i.getPaidCents())
                        .currency(i.getCurrency())
                        .createdAt(i.getCreatedAt())
                        .build())
                .toList();

        return AdminBookingDetail.FinanceSummary.builder()
                .bookingTotalCents(b.getTotalAmountCents())
                .invoicedCents(invoiced)
                .invoiceOutstandingCents(invoiced - invoicePaid)
                .paidCents(bookingPaid)
                .advanceUnallocatedCents(advanceUnallocated)
                .vendorCommittedCents(vendorCommitted)
                .vendorPaidCents(vendorPaid)
                .directExpenseCents(directExpense)
                .totalCostCents(totalCost)
                .grossMarginCents(margin)
                .invoices(invoiceRefs)
                .build();
    }

    private AdminBookingDetail.PoRef toPoRef(PurchaseOrder p) {
        return AdminBookingDetail.PoRef.builder()
                .id(p.getId())
                .reference(p.getReference())
                .status(p.getStatus().name().toLowerCase())
                .vendorId(p.getVendor().getId())
                .vendorName(p.getVendor().getName())
                .vendorCategory(p.getVendor().getCategory().name().toLowerCase())
                .issueDate(p.getIssueDate())
                .expectedDelivery(p.getExpectedDelivery())
                .totalCents(p.getTotalCents())
                .paidCents(p.getPaidCents())
                .currency(p.getCurrency())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private Booking.BookingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }

    private Booking.BookingStatus parseStatusStrict(String raw) {
        try {
            return Booking.BookingStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw +
                    "'. Allowed: confirmed, in_progress, completed, cancelled, postponed.");
        }
    }

    private static String normalizeTaskStatus(String raw) {
        Set<String> allowed = Set.of("todo", "in_progress", "done", "blocked");
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw new BadRequestException("Invalid task status '" + raw +
                    "'. Allowed: todo, in_progress, done, blocked.");
        }
        return v;
    }
}
