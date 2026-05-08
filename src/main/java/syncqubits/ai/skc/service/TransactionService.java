package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.transaction.AdminTransactionSummary;
import syncqubits.ai.skc.dto.transaction.TransactionCreateRequest;
import syncqubits.ai.skc.dto.transaction.TransactionUpdateRequest;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.Invoice;
import syncqubits.ai.skc.entity.PurchaseOrder;
import syncqubits.ai.skc.entity.Transaction;
import syncqubits.ai.skc.entity.Vendor;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BookingRepository;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.InvoiceRepository;
import syncqubits.ai.skc.repository.PurchaseOrderRepository;
import syncqubits.ai.skc.repository.TransactionRepository;
import syncqubits.ai.skc.repository.VendorRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Single ledger of cash movement. Owns ALL incoming (client → us) and
 * outgoing (us → vendor / expense) money events; recording one event
 * cascades the rollups —
 *
 *   • {@code Booking.paidAmountCents}      ← non-refunded INCOMING for booking
 *   • {@code Booking.directExpenseCents}   ← non-refunded OUTGOING for booking, no PO
 *   • {@code Invoice.paidCents}            ← non-refunded INCOMING tagged to invoice
 *   • {@code PurchaseOrder.paidCents}      ← non-refunded OUTGOING tagged to PO
 *   • {@code Vendor.totalSpendCents}       ← refreshed when an OUTGOING is tagged to a vendor
 *   • {@code Client.lifetimeValueCents}    ← rolled forward when a booking moves to COMPLETED
 *     (handled by BookingService — Transaction touches the booking aggregate)
 *
 * One write path means one place to enforce the invariants. Refunds are
 * a status flip on the original row, not a new row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BookingRepository bookingRepository;
    private final InvoiceService invoiceService;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionSummary> list(String q,
                                                      String direction,
                                                      String status,
                                                      String method,
                                                      String category,
                                                      UUID bookingId,
                                                      UUID invoiceId,
                                                      UUID vendorId,
                                                      UUID poId,
                                                      UUID clientId,
                                                      Instant fromDate,
                                                      Instant toDate,
                                                      int page,
                                                      int size,
                                                      String sortField,
                                                      Sort.Direction sortDir) {
        Sort sort = Sort.by(sortDir == null ? Sort.Direction.DESC : sortDir,
                sortField == null || sortField.isBlank() ? "paidAt" : sortField);

        Page<Transaction> result = transactionRepository.searchTransactions(
                q == null ? "" : q.trim(),
                parseDirection(direction),
                parseStatus(status),
                parseMethod(method),
                parseCategory(category),
                bookingId, invoiceId, vendorId, poId, clientId,
                fromDate, toDate,
                PageRequest.of(page, size, sort));

        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminTransactionSummary detail(UUID id) {
        return toSummary(mustFind(id));
    }

    /* ──────────────────────────────────── mutations ─────────────────────────────── */

    @Transactional
    public AdminTransactionSummary record(TransactionCreateRequest req) {
        Transaction.Direction direction = parseDirectionStrict(req.getDirection());

        Transaction t = Transaction.builder()
                .reference(allocateReference())
                .direction(direction)
                .amountCents(req.getAmountCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? "INR" : req.getCurrency())
                .method(req.getMethod() == null ? Transaction.Method.CASH : parseMethodStrict(req.getMethod()))
                .paidAt(req.getPaidAt() == null ? Instant.now() : req.getPaidAt())
                .transactionRef(req.getTransactionRef())
                .description(req.getDescription())
                .notes(req.getNotes())
                .receiptUrl(req.getReceiptUrl())
                .build();

        // Resolve attribution per direction.
        if (direction == Transaction.Direction.INCOMING) {
            // Booking is required so the payment hits a P&L. Either supply
            // it directly or let it derive from the invoice.
            Invoice inv = null;
            if (req.getInvoiceId() != null) {
                inv = invoiceRepository.findById(req.getInvoiceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Invoice " + req.getInvoiceId() + " not found"));
                t.setInvoice(inv);
            }
            UUID bookingId = req.getBookingId() != null ? req.getBookingId()
                    : (inv != null ? inv.getBooking().getId() : null);
            if (bookingId == null) {
                throw new BadRequestException("Incoming transaction needs a booking or invoice.");
            }
            Booking b = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
            t.setBooking(b);

            UUID clientId = req.getClientId() != null ? req.getClientId() : b.getClient().getId();
            Client c = clientRepository.findById(clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client " + clientId + " not found"));
            t.setClient(c);
            // category is meaningless for INCOMING.
            t.setCategory(null);
        } else { // OUTGOING
            if (req.getCategory() == null || req.getCategory().isBlank()) {
                throw new BadRequestException("Outgoing transaction requires a category.");
            }
            t.setCategory(parseCategoryStrict(req.getCategory()));

            if (req.getVendorId() != null) {
                t.setVendor(vendorRepository.findById(req.getVendorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Vendor " + req.getVendorId() + " not found")));
            }
            if (req.getPurchaseOrderId() != null) {
                t.setPurchaseOrder(purchaseOrderRepository.findById(req.getPurchaseOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("PO " + req.getPurchaseOrderId() + " not found")));
                if (t.getVendor() == null) {
                    t.setVendor(t.getPurchaseOrder().getVendor());
                }
            }
            if (req.getBookingId() != null) {
                t.setBooking(bookingRepository.findById(req.getBookingId())
                        .orElseThrow(() -> new ResourceNotFoundException("Booking " + req.getBookingId() + " not found")));
            }
        }

        Transaction saved = transactionRepository.save(t);
        propagateRollups(saved);

        systemLogService.logEmail(direction == Transaction.Direction.INCOMING ? "payment_recorded" : "expense_recorded",
                "success", saved.getId(), "transaction",
                Map.of("reference", saved.getReference(),
                       "amount", saved.getAmountCents(),
                       "direction", saved.getDirection().name()));

        return toSummary(saved);
    }

    @Transactional
    public AdminTransactionSummary update(UUID id, TransactionUpdateRequest req) {
        Transaction t = mustFind(id);
        Map<String, Object> changes = new HashMap<>();

        if (req.getAmountCents() != null) { changes.put("amount", req.getAmountCents()); t.setAmountCents(req.getAmountCents()); }
        if (req.getCurrency()    != null && !req.getCurrency().isBlank()) t.setCurrency(req.getCurrency());
        if (req.getMethod()      != null) t.setMethod(parseMethodStrict(req.getMethod()));
        if (req.getPaidAt()      != null) t.setPaidAt(req.getPaidAt());
        if (req.getTransactionRef() != null) t.setTransactionRef(req.getTransactionRef());

        if (req.getCategory() != null && t.getDirection() == Transaction.Direction.OUTGOING) {
            t.setCategory(parseCategoryStrict(req.getCategory()));
        }

        if (req.getStatus() != null) {
            Transaction.TxnStatus next = parseStatusStrict(req.getStatus());
            if (next == Transaction.TxnStatus.REFUNDED) {
                throw new BadRequestException("Use the /refund endpoint to refund a transaction.");
            }
            t.setStatus(next);
        }

        if (req.getDescription() != null) t.setDescription(req.getDescription());
        if (req.getNotes()       != null) t.setNotes(req.getNotes());
        if (req.getReceiptUrl()  != null) t.setReceiptUrl(req.getReceiptUrl());

        Transaction saved = transactionRepository.save(t);
        propagateRollups(saved);

        if (!changes.isEmpty()) {
            systemLogService.logEmail("transaction_updated", "success", saved.getId(), "transaction", changes);
        }
        return toSummary(saved);
    }

    @Transactional
    public AdminTransactionSummary refund(UUID id, String reason) {
        Transaction t = mustFind(id);
        if (t.getStatus() == Transaction.TxnStatus.REFUNDED) {
            throw new BadRequestException("Transaction is already refunded.");
        }
        if (t.getDirection() != Transaction.Direction.INCOMING) {
            throw new BadRequestException("Only incoming transactions can be refunded; reverse outgoing entries with a new transaction.");
        }
        t.setStatus(Transaction.TxnStatus.REFUNDED);
        t.setRefundedAt(Instant.now());
        t.setRefundReason(reason);
        Transaction saved = transactionRepository.save(t);
        propagateRollups(saved);
        systemLogService.logEmail("payment_refunded", "success", saved.getId(), "transaction",
                Map.of("reference", saved.getReference(), "amount", saved.getAmountCents()));
        return toSummary(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Transaction t = mustFind(id);
        Transaction snapshot = t;  // capture refs before deletion
        transactionRepository.delete(t);
        propagateRollups(snapshot);
        systemLogService.logEmail("transaction_deleted", "success", id, "transaction", Map.of());
    }

    /* ──────────────────────────────────── rollup cascade ────────────────────────── */

    /** Refresh every aggregate touched by a transaction add/update/delete. */
    private void propagateRollups(Transaction t) {
        if (t.getBooking() != null) {
            Booking b = t.getBooking();
            b.setPaidAmountCents(transactionRepository.sumPaidForBooking(b.getId()));
            b.setDirectExpenseCents(transactionRepository.sumDirectExpenseForBooking(b.getId()));
            bookingRepository.save(b);
        }
        if (t.getInvoice() != null) {
            invoiceService.syncPaid(t.getInvoice().getId());
        }
        if (t.getPurchaseOrder() != null) {
            PurchaseOrder po = t.getPurchaseOrder();
            long paid = transactionRepository.sumPaidForPo(po.getId());
            po.setPaidCents(paid);
            if (paid >= po.getTotalCents() && po.getTotalCents() > 0
                    && po.getStatus() != PurchaseOrder.PoStatus.CANCELLED) {
                po.setStatus(PurchaseOrder.PoStatus.PAID);
            }
            purchaseOrderRepository.save(po);
        }
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    private Transaction mustFind(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
    }

    private String allocateReference() {
        long n = transactionRepository.nextReferenceNumber();
        int year = LocalDate.now(BUSINESS_ZONE).getYear();
        return String.format("TXN-%d-%06d", year, n);
    }

    /* ── mappers ── */

    private AdminTransactionSummary toSummary(Transaction t) {
        AdminTransactionSummary.AdminTransactionSummaryBuilder b = AdminTransactionSummary.builder()
                .id(t.getId())
                .reference(t.getReference())
                .direction(t.getDirection().name().toLowerCase())
                .status(t.getStatus().name().toLowerCase())
                .method(t.getMethod().name().toLowerCase())
                .category(t.getCategory() == null ? null : t.getCategory().name().toLowerCase())
                .amountCents(t.getAmountCents())
                .currency(t.getCurrency())
                .paidAt(t.getPaidAt())
                .transactionRef(t.getTransactionRef())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt());

        if (t.getClient() != null)        { b.clientId(t.getClient().getId()).clientName(t.getClient().getName()); }
        if (t.getInvoice() != null)       { b.invoiceId(t.getInvoice().getId()).invoiceReference(t.getInvoice().getReference()); }
        if (t.getVendor() != null)        { b.vendorId(t.getVendor().getId()).vendorName(t.getVendor().getName()); }
        if (t.getPurchaseOrder() != null) { b.purchaseOrderId(t.getPurchaseOrder().getId()).purchaseOrderReference(t.getPurchaseOrder().getReference()); }
        if (t.getBooking() != null)       { b.bookingId(t.getBooking().getId()).bookingReference(t.getBooking().getReference()); }
        return b.build();
    }

    /* ── parsers ── */

    private Transaction.Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseDirectionStrict(raw);
    }
    private Transaction.Direction parseDirectionStrict(String raw) {
        try { return Transaction.Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid direction '" + raw + "'. Allowed: incoming, outgoing.");
        }
    }
    private Transaction.TxnStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }
    private Transaction.TxnStatus parseStatusStrict(String raw) {
        try { return Transaction.TxnStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '" + raw + "'. Allowed: recorded, reconciled, refunded.");
        }
    }
    private Transaction.Method parseMethod(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseMethodStrict(raw);
    }
    private Transaction.Method parseMethodStrict(String raw) {
        try { return Transaction.Method.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid method '" + raw + "'. Allowed: cash, upi, bank_transfer, cheque, card, other.");
        }
    }
    private Transaction.ExpenseCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseCategoryStrict(raw);
    }
    private Transaction.ExpenseCategory parseCategoryStrict(String raw) {
        try { return Transaction.ExpenseCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid category '" + raw + "'.");
        }
    }

    /** Per-booking P&L inputs for the BookingFinanceService. */
    @Transactional(readOnly = true)
    public List<Transaction> recentForBooking(UUID bookingId, int limit) {
        List<Transaction> all = transactionRepository.findByBookingId(bookingId);
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
