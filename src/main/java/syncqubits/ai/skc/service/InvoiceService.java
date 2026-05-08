package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.invoice.AdminInvoiceDetail;
import syncqubits.ai.skc.dto.invoice.AdminInvoiceSummary;
import syncqubits.ai.skc.dto.invoice.InvoiceCreateRequest;
import syncqubits.ai.skc.dto.invoice.InvoiceItemRequest;
import syncqubits.ai.skc.dto.invoice.InvoiceUpdateRequest;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.Invoice;
import syncqubits.ai.skc.entity.Transaction;
import syncqubits.ai.skc.entity.embedded.LineItemDoc;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BookingRepository;
import syncqubits.ai.skc.repository.InvoiceRepository;
import syncqubits.ai.skc.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invoice lifecycle.
 *
 * Recomputes subtotal/total/paid on every mutation so the list view never
 * has to fan out into items or transactions. {@code paidCents} is the
 * authoritative roll-up of non-refunded INCOMING transactions tagged to
 * this invoice; the {@link #syncPaid(UUID)} hook lets {@link
 * TransactionService} refresh it whenever a payment lands or refunds.
 *
 * Status transitions:
 *   DRAFT          → ISSUED                     (admin "issue")
 *   ISSUED         → PARTIALLY_PAID / PAID      (auto, on payment)
 *   ISSUED         → OVERDUE                    (auto, when due_date passes)
 *   ISSUED / PP    → VOID                       (admin "void"; terminal)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final TransactionRepository transactionRepository;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<AdminInvoiceSummary> list(String q,
                                                  String status,
                                                  UUID bookingId,
                                                  UUID clientId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  int page,
                                                  int size,
                                                  String sortField,
                                                  Sort.Direction direction) {
        Invoice.InvoiceStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Invoice> result = invoiceRepository.searchInvoices(
                q == null ? "" : q.trim(), statusEnum, bookingId, clientId,
                fromDate, toDate, PageRequest.of(page, size, sort));

        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminInvoiceDetail detail(UUID id) {
        return toDetail(mustFind(id));
    }

    /* ──────────────────────────────────── mutations ─────────────────────────────── */

    @Transactional
    public AdminInvoiceDetail create(InvoiceCreateRequest req) {
        Booking b = bookingRepository.findById(req.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + req.getBookingId() + " not found"));

        Invoice inv = Invoice.builder()
                .reference(allocateReference())
                .booking(b)
                .client(b.getClient())
                .status(req.getStatus() == null || req.getStatus().isBlank()
                        ? Invoice.InvoiceStatus.DRAFT
                        : parseStatusStrict(req.getStatus()))
                .issueDate(req.getIssueDate() == null ? LocalDate.now(BUSINESS_ZONE) : req.getIssueDate())
                .dueDate(req.getDueDate())
                .taxCents(req.getTaxCents() == null ? 0L : req.getTaxCents())
                .discountCents(req.getDiscountCents() == null ? 0L : req.getDiscountCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? b.getCurrency() : req.getCurrency())
                .terms(req.getTerms())
                .notes(req.getNotes())
                .build();

        if (Boolean.TRUE.equals(req.getSeedFromBooking()) && (req.getItems() == null || req.getItems().isEmpty())) {
            seedFromBooking(inv, b);
        } else if (req.getItems() != null) {
            int pos = 0;
            for (InvoiceItemRequest line : req.getItems()) {
                inv.getItems().add(buildItem(line, pos++));
            }
        }
        recomputeTotals(inv);

        Invoice saved = invoiceRepository.save(inv);
        bumpBookingInvoicedCents(b);

        systemLogService.logEmail("invoice_created", "success", saved.getId(), "invoice",
                Map.of("reference", saved.getReference(),
                       "booking", b.getId().toString(),
                       "total", saved.getTotalCents()));
        return toDetail(saved);
    }

    @Transactional
    public AdminInvoiceDetail update(UUID id, InvoiceUpdateRequest req) {
        Invoice inv = mustFind(id);
        Map<String, Object> changes = new HashMap<>();

        if (req.getStatus() != null) {
            Invoice.InvoiceStatus next = parseStatusStrict(req.getStatus());
            validateStatusTransition(inv.getStatus(), next);
            if (next != inv.getStatus()) {
                changes.put("status", Map.of("from", inv.getStatus().name().toLowerCase(),
                                             "to",   next.name().toLowerCase()));
                if (next == Invoice.InvoiceStatus.ISSUED && inv.getSentAt() == null) {
                    inv.setSentAt(Instant.now());
                }
                if (next == Invoice.InvoiceStatus.VOID) {
                    inv.setVoidedAt(Instant.now());
                }
                inv.setStatus(next);
            }
        }

        if (req.getIssueDate()     != null) inv.setIssueDate(req.getIssueDate());
        if (req.getDueDate()       != null) inv.setDueDate(req.getDueDate());
        if (req.getTaxCents()      != null) inv.setTaxCents(req.getTaxCents());
        if (req.getDiscountCents() != null) inv.setDiscountCents(req.getDiscountCents());
        if (req.getCurrency()      != null && !req.getCurrency().isBlank()) inv.setCurrency(req.getCurrency());
        if (req.getTerms()         != null) inv.setTerms(req.getTerms());
        if (req.getNotes()         != null) inv.setNotes(req.getNotes());

        if (req.getItems() != null) {
            applyItems(inv, req.getItems());
        }

        recomputeTotals(inv);

        Invoice saved = invoiceRepository.save(inv);
        bumpBookingInvoicedCents(saved.getBooking());

        if (!changes.isEmpty()) {
            systemLogService.logEmail("invoice_updated", "success", saved.getId(), "invoice", changes);
        }
        return toDetail(saved);
    }

    @Transactional
    public AdminInvoiceDetail markIssued(UUID id) {
        Invoice inv = mustFind(id);
        if (inv.getStatus() == Invoice.InvoiceStatus.DRAFT) {
            inv.setStatus(Invoice.InvoiceStatus.ISSUED);
            inv.setSentAt(Instant.now());
        }
        return toDetail(invoiceRepository.save(inv));
    }

    @Transactional
    public AdminInvoiceDetail voidInvoice(UUID id) {
        Invoice inv = mustFind(id);
        if (inv.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new BadRequestException("Paid invoices cannot be voided. Issue a refund instead.");
        }
        inv.setStatus(Invoice.InvoiceStatus.VOID);
        inv.setVoidedAt(Instant.now());
        Invoice saved = invoiceRepository.save(inv);
        bumpBookingInvoicedCents(saved.getBooking());
        return toDetail(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Invoice inv = mustFind(id);
        if (inv.getStatus() != Invoice.InvoiceStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT invoices can be deleted. Void issued invoices instead.");
        }
        Booking b = inv.getBooking();
        invoiceRepository.delete(inv);
        bumpBookingInvoicedCents(b);
        systemLogService.logEmail("invoice_deleted", "success", id, "invoice", Map.of());
    }

    /**
     * Refresh {@code paidCents} from non-refunded incoming transactions.
     * Called by {@link TransactionService} whenever a payment is recorded
     * against this invoice (or refunded). Auto-advances status when paid
     * fully or due_date passes.
     */
    @Transactional
    public void syncPaid(UUID invoiceId) {
        Invoice inv = mustFind(invoiceId);
        long paid = transactionRepository.sumPaidForInvoice(invoiceId);
        inv.setPaidCents(paid);

        // Status auto-progression — leave terminal states alone.
        if (inv.getStatus() != Invoice.InvoiceStatus.DRAFT
                && inv.getStatus() != Invoice.InvoiceStatus.VOID) {
            if (paid >= inv.getTotalCents() && inv.getTotalCents() > 0) {
                inv.setStatus(Invoice.InvoiceStatus.PAID);
            } else if (paid > 0) {
                inv.setStatus(Invoice.InvoiceStatus.PARTIALLY_PAID);
            } else if (inv.getDueDate() != null
                    && inv.getDueDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
                inv.setStatus(Invoice.InvoiceStatus.OVERDUE);
            } else {
                inv.setStatus(Invoice.InvoiceStatus.ISSUED);
            }
        }
        invoiceRepository.save(inv);
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    private void seedFromBooking(Invoice inv, Booking b) {
        // Auto-fill: a single line representing the booking's package and
        // headcount priced at (totalAmountCents − already-invoiced).
        long alreadyInvoiced = invoiceRepository.sumInvoicedForBooking(b.getId());
        long balance = Math.max(0, (b.getTotalAmountCents() == null ? 0L : b.getTotalAmountCents()) - alreadyInvoiced);
        if (balance == 0) return;

        String desc = (b.getPackageName() == null ? b.getEventType() : b.getPackageName())
                + " — " + b.getGuestCount() + " guests";
        LineItemDoc item = LineItemDoc.builder()
                .id(UUID.randomUUID())
                .description(desc)
                .quantity(BigDecimal.ONE)
                .unit("event")
                .unitPriceCents(balance)
                .lineTotalCents(balance)
                .position(0)
                .build();
        inv.getItems().add(item);
    }

    private void applyItems(Invoice inv, List<InvoiceItemRequest> incoming) {
        Map<UUID, LineItemDoc> existing = new HashMap<>();
        for (LineItemDoc it : inv.getItems()) {
            if (it.getId() != null) existing.put(it.getId(), it);
        }

        List<LineItemDoc> next = new ArrayList<>();
        int pos = 0;
        for (InvoiceItemRequest req : incoming) {
            if (req.getId() != null && existing.containsKey(req.getId())) {
                LineItemDoc it = existing.get(req.getId());
                it.setDescription(req.getDescription());
                it.setQuantity(req.getQuantity());
                it.setUnit(req.getUnit() == null || req.getUnit().isBlank() ? "each" : req.getUnit());
                it.setUnitPriceCents(req.getUnitPriceCents());
                it.setLineTotalCents(computeLineTotal(req.getQuantity(), req.getUnitPriceCents()));
                it.setPosition(req.getPosition() == null ? pos : req.getPosition());
                it.setNotes(req.getNotes());
                next.add(it);
            } else {
                next.add(buildItem(req, pos));
            }
            pos++;
        }
        inv.setItems(next);
    }

    private LineItemDoc buildItem(InvoiceItemRequest req, int pos) {
        return LineItemDoc.builder()
                .id(UUID.randomUUID())
                .description(req.getDescription())
                .quantity(req.getQuantity())
                .unit(req.getUnit() == null || req.getUnit().isBlank() ? "each" : req.getUnit())
                .unitPriceCents(req.getUnitPriceCents())
                .lineTotalCents(computeLineTotal(req.getQuantity(), req.getUnitPriceCents()))
                .position(req.getPosition() == null ? pos : req.getPosition())
                .notes(req.getNotes())
                .build();
    }

    private static long computeLineTotal(BigDecimal qty, Long unitPrice) {
        if (qty == null || unitPrice == null) return 0L;
        return qty.multiply(BigDecimal.valueOf(unitPrice))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private void recomputeTotals(Invoice inv) {
        long subtotal = inv.getItems().stream()
                .mapToLong(it -> it.getLineTotalCents() == null ? 0L : it.getLineTotalCents())
                .sum();
        long tax = inv.getTaxCents() == null ? 0L : inv.getTaxCents();
        long discount = inv.getDiscountCents() == null ? 0L : inv.getDiscountCents();
        inv.setSubtotalCents(subtotal);
        inv.setTotalCents(Math.max(0, subtotal + tax - discount));
    }

    private void bumpBookingInvoicedCents(Booking b) {
        if (b == null) return;
        long invoiced = invoiceRepository.sumInvoicedForBooking(b.getId());
        b.setInvoicedAmountCents(invoiced);
        bookingRepository.save(b);
    }

    private void validateStatusTransition(Invoice.InvoiceStatus from, Invoice.InvoiceStatus to) {
        if (from == to) return;
        if (from == Invoice.InvoiceStatus.PAID && to != Invoice.InvoiceStatus.VOID) {
            throw new BadRequestException("PAID invoices can only move to VOID.");
        }
        if (from == Invoice.InvoiceStatus.VOID) {
            throw new BadRequestException("VOID invoices are terminal.");
        }
    }

    private String allocateReference() {
        long n = invoiceRepository.nextReferenceNumber();
        int year = LocalDate.now(BUSINESS_ZONE).getYear();
        return String.format("INV-%d-%06d", year, n);
    }

    private Invoice mustFind(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice " + id + " not found"));
    }

    /* ── mappers ── */

    private AdminInvoiceSummary toSummary(Invoice inv) {
        Booking b = inv.getBooking();
        Client c = inv.getClient();
        long total = inv.getTotalCents() == null ? 0L : inv.getTotalCents();
        long paid  = inv.getPaidCents()  == null ? 0L : inv.getPaidCents();
        return AdminInvoiceSummary.builder()
                .id(inv.getId())
                .reference(inv.getReference())
                .status(inv.getStatus().name().toLowerCase())
                .bookingId(b.getId())
                .bookingReference(b.getReference())
                .clientId(c.getId())
                .clientName(c.getName())
                .clientEmail(c.getEmail())
                .issueDate(inv.getIssueDate())
                .dueDate(inv.getDueDate())
                .totalCents(total)
                .paidCents(paid)
                .outstandingCents(total - paid)
                .currency(inv.getCurrency())
                .itemCount(inv.getItems() == null ? 0 : inv.getItems().size())
                .createdAt(inv.getCreatedAt())
                .build();
    }

    private AdminInvoiceDetail toDetail(Invoice inv) {
        Booking b = inv.getBooking();
        Client c = inv.getClient();

        List<AdminInvoiceDetail.Item> items = inv.getItems().stream()
                .sorted(Comparator.comparing(LineItemDoc::getPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(it -> AdminInvoiceDetail.Item.builder()
                        .id(it.getId())
                        .description(it.getDescription())
                        .quantity(it.getQuantity())
                        .unit(it.getUnit())
                        .unitPriceCents(it.getUnitPriceCents())
                        .lineTotalCents(it.getLineTotalCents())
                        .position(it.getPosition())
                        .notes(it.getNotes())
                        .build())
                .toList();

        List<AdminInvoiceDetail.PaymentRef> payments = transactionRepository
                .findByInvoiceId(inv.getId()).stream()
                .filter(t -> t.getDirection() == Transaction.Direction.INCOMING)
                .map(t -> AdminInvoiceDetail.PaymentRef.builder()
                        .id(t.getId())
                        .reference(t.getReference())
                        .amountCents(t.getAmountCents())
                        .currency(t.getCurrency())
                        .method(t.getMethod().name().toLowerCase())
                        .status(t.getStatus().name().toLowerCase())
                        .paidAt(t.getPaidAt())
                        .transactionRef(t.getTransactionRef())
                        .build())
                .toList();

        return AdminInvoiceDetail.builder()
                .id(inv.getId())
                .reference(inv.getReference())
                .status(inv.getStatus().name().toLowerCase())
                .booking(AdminInvoiceDetail.BookingRef.builder()
                        .id(b.getId())
                        .reference(b.getReference())
                        .eventType(b.getEventType())
                        .eventDate(b.getEventDate())
                        .build())
                .client(AdminInvoiceDetail.ClientRef.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .email(c.getEmail())
                        .phone(c.getPhone())
                        .companyName(c.getCompanyName())
                        .build())
                .issueDate(inv.getIssueDate())
                .dueDate(inv.getDueDate())
                .sentAt(inv.getSentAt())
                .voidedAt(inv.getVoidedAt())
                .subtotalCents(inv.getSubtotalCents())
                .taxCents(inv.getTaxCents())
                .discountCents(inv.getDiscountCents())
                .totalCents(inv.getTotalCents())
                .paidCents(inv.getPaidCents())
                .currency(inv.getCurrency())
                .terms(inv.getTerms())
                .notes(inv.getNotes())
                .items(items)
                .payments(payments)
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    private Invoice.InvoiceStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }

    private Invoice.InvoiceStatus parseStatusStrict(String raw) {
        try {
            return Invoice.InvoiceStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice status '" + raw +
                    "'. Allowed: draft, issued, partially_paid, paid, overdue, void.");
        }
    }
}
