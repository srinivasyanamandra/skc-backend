package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.po.AdminPurchaseOrderDetail;
import syncqubits.ai.skc.dto.po.AdminPurchaseOrderSummary;
import syncqubits.ai.skc.dto.po.PurchaseOrderCreateRequest;
import syncqubits.ai.skc.dto.po.PurchaseOrderItemRequest;
import syncqubits.ai.skc.dto.po.PurchaseOrderUpdateRequest;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.PurchaseOrder;
import syncqubits.ai.skc.entity.Vendor;
import syncqubits.ai.skc.entity.embedded.LineItemDoc;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.BookingRepository;
import syncqubits.ai.skc.repository.PurchaseOrderRepository;
import syncqubits.ai.skc.repository.VendorRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Purchase order lifecycle. Items used to live in {@code purchase_order_items};
 * they're now a JSONB array on the PO row. The service still recomputes
 * subtotals/totals on every mutation so list rendering doesn't need to fan
 * out into items.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;
    private final BookingRepository bookingRepository;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<AdminPurchaseOrderSummary> list(String q,
                                                        String status,
                                                        UUID vendorId,
                                                        UUID bookingId,
                                                        LocalDate fromDate,
                                                        LocalDate toDate,
                                                        int page,
                                                        int size,
                                                        String sortField,
                                                        Sort.Direction direction) {
        PurchaseOrder.PoStatus statusEnum = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<PurchaseOrder> result = purchaseOrderRepository.searchPurchaseOrders(
                q == null ? "" : q.trim(), statusEnum, vendorId, bookingId,
                fromDate, toDate, PageRequest.of(page, size, sort));

        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminPurchaseOrderDetail detail(UUID id) {
        PurchaseOrder po = mustFind(id);
        return toDetail(po);
    }

    /* ──────────────────────────────────── mutations ─────────────────────────────── */

    @Transactional
    public AdminPurchaseOrderDetail create(PurchaseOrderCreateRequest req) {
        Vendor vendor = vendorRepository.findById(req.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor " + req.getVendorId() + " not found"));
        if (vendor.getStatus() == Vendor.VendorStatus.BLOCKED) {
            throw new BadRequestException("Vendor is blocked. Unblock the vendor before raising new POs.");
        }

        Booking booking = null;
        if (req.getBookingId() != null) {
            booking = bookingRepository.findById(req.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking " + req.getBookingId() + " not found"));
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .reference(allocateReference())
                .vendor(vendor)
                .booking(booking)
                .status(req.getStatus() == null || req.getStatus().isBlank()
                        ? PurchaseOrder.PoStatus.DRAFT
                        : parseStatusStrict(req.getStatus()))
                .issueDate(req.getIssueDate() == null ? LocalDate.now(BUSINESS_ZONE) : req.getIssueDate())
                .expectedDelivery(req.getExpectedDelivery())
                .taxCents(req.getTaxCents() == null ? 0L : req.getTaxCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? vendor.getCurrency() : req.getCurrency())
                .internalNotes(req.getInternalNotes())
                .vendorNotes(req.getVendorNotes())
                .build();

        if (req.getItems() != null) {
            int pos = 0;
            for (PurchaseOrderItemRequest line : req.getItems()) {
                po.getItems().add(buildItem(line, pos++));
            }
        }
        recomputeTotals(po);
        PurchaseOrder saved = purchaseOrderRepository.save(po);

        bumpVendorAggregates(vendor);

        systemLogService.logEmail("po_created", "success", saved.getId(), "purchase_order",
                Map.of("reference", saved.getReference(),
                       "vendor", vendor.getId().toString(),
                       "items", saved.getItems().size()));
        return toDetail(saved);
    }

    @Transactional
    public AdminPurchaseOrderDetail update(UUID id, PurchaseOrderUpdateRequest req) {
        PurchaseOrder po = mustFind(id);
        Map<String, Object> changes = new HashMap<>();

        if (req.getBookingId() != null) {
            Booking b = bookingRepository.findById(req.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking " + req.getBookingId() + " not found"));
            po.setBooking(b);
        }

        if (req.getStatus() != null) {
            PurchaseOrder.PoStatus next = parseStatusStrict(req.getStatus());
            validateStatusTransition(po.getStatus(), next);
            if (next != po.getStatus()) {
                changes.put("status", Map.of("from", po.getStatus().name().toLowerCase(),
                                             "to",   next.name().toLowerCase()));
                po.setStatus(next);
                if (next == PurchaseOrder.PoStatus.RECEIVED && po.getReceivedAt() == null) {
                    po.setReceivedAt(Instant.now());
                }
                if (next == PurchaseOrder.PoStatus.PAID) {
                    po.setPaidCents(po.getTotalCents());
                }
            }
        }

        if (req.getIssueDate()        != null) po.setIssueDate(req.getIssueDate());
        if (req.getExpectedDelivery() != null) po.setExpectedDelivery(req.getExpectedDelivery());
        if (req.getTaxCents()         != null) po.setTaxCents(req.getTaxCents());
        if (req.getPaidCents()        != null) {
            if (req.getPaidCents() > po.getTotalCents()) {
                throw new BadRequestException("Paid amount cannot exceed total.");
            }
            po.setPaidCents(req.getPaidCents());
        }
        if (req.getCurrency()      != null && !req.getCurrency().isBlank()) po.setCurrency(req.getCurrency());
        if (req.getInternalNotes() != null) po.setInternalNotes(req.getInternalNotes());
        if (req.getVendorNotes()   != null) po.setVendorNotes(req.getVendorNotes());

        if (req.getItems() != null) {
            applyItems(po, req.getItems());
        }
        recomputeTotals(po);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        bumpVendorAggregates(po.getVendor());

        if (!changes.isEmpty()) {
            systemLogService.logEmail("po_updated", "success", saved.getId(), "purchase_order", changes);
        }
        return toDetail(saved);
    }

    @Transactional
    public AdminPurchaseOrderDetail recordPayment(UUID id, long paidCents) {
        if (paidCents < 0) throw new BadRequestException("paidCents must be ≥ 0");
        PurchaseOrder po = mustFind(id);
        if (paidCents > po.getTotalCents()) {
            throw new BadRequestException("Paid amount cannot exceed total.");
        }
        po.setPaidCents(paidCents);
        if (paidCents == po.getTotalCents() && po.getStatus() != PurchaseOrder.PoStatus.CANCELLED) {
            po.setStatus(PurchaseOrder.PoStatus.PAID);
        }
        return toDetail(purchaseOrderRepository.save(po));
    }

    @Transactional
    public void delete(UUID id) {
        PurchaseOrder po = mustFind(id);
        if (po.getStatus() != PurchaseOrder.PoStatus.DRAFT
                && po.getStatus() != PurchaseOrder.PoStatus.CANCELLED) {
            throw new BadRequestException(
                    "Only DRAFT or CANCELLED purchase orders can be deleted. Cancel the PO first.");
        }
        Vendor v = po.getVendor();
        purchaseOrderRepository.delete(po);
        bumpVendorAggregates(v);
        systemLogService.logEmail("po_deleted", "success", id, "purchase_order", Map.of());
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    /** Diff strategy on the JSONB items list: rows with an {@code id} that
     *  matches an existing item are updated in place; new rows (no id or
     *  unknown id) are appended; existing rows whose ids are absent from
     *  the request are dropped. */
    private void applyItems(PurchaseOrder po, List<PurchaseOrderItemRequest> incoming) {
        Map<UUID, LineItemDoc> existing = new HashMap<>();
        for (LineItemDoc it : po.getItems()) {
            if (it.getId() != null) existing.put(it.getId(), it);
        }

        Set<UUID> keep = new HashSet<>();
        List<LineItemDoc> next = new ArrayList<>();
        int pos = 0;
        for (PurchaseOrderItemRequest req : incoming) {
            if (req.getId() != null && existing.containsKey(req.getId())) {
                LineItemDoc it = existing.get(req.getId());
                it.setDescription(req.getDescription());
                it.setQuantity(req.getQuantity());
                it.setUnit(req.getUnit() == null || req.getUnit().isBlank() ? "each" : req.getUnit());
                it.setUnitPriceCents(req.getUnitPriceCents());
                it.setLineTotalCents(computeLineTotal(req.getQuantity(), req.getUnitPriceCents()));
                it.setPosition(req.getPosition() == null ? pos : req.getPosition());
                it.setNotes(req.getNotes());
                if (req.getRateCardId() != null) it.setRateCardId(req.getRateCardId());
                next.add(it);
                keep.add(it.getId());
            } else {
                next.add(buildItem(req, pos));
            }
            pos++;
        }
        po.setItems(next);
    }

    private LineItemDoc buildItem(PurchaseOrderItemRequest req, int pos) {
        return LineItemDoc.builder()
                .id(UUID.randomUUID())
                .description(req.getDescription())
                .quantity(req.getQuantity())
                .unit(req.getUnit() == null || req.getUnit().isBlank() ? "each" : req.getUnit())
                .unitPriceCents(req.getUnitPriceCents())
                .lineTotalCents(computeLineTotal(req.getQuantity(), req.getUnitPriceCents()))
                .position(req.getPosition() == null ? pos : req.getPosition())
                .rateCardId(req.getRateCardId())
                .notes(req.getNotes())
                .build();
    }

    private static long computeLineTotal(BigDecimal qty, Long unitPrice) {
        if (qty == null || unitPrice == null) return 0L;
        return qty.multiply(BigDecimal.valueOf(unitPrice))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private void recomputeTotals(PurchaseOrder po) {
        long subtotal = po.getItems().stream()
                .mapToLong(it -> it.getLineTotalCents() == null ? 0L : it.getLineTotalCents())
                .sum();
        long tax = po.getTaxCents() == null ? 0L : po.getTaxCents();
        po.setSubtotalCents(subtotal);
        po.setTotalCents(subtotal + tax);
    }

    private void bumpVendorAggregates(Vendor v) {
        long total = purchaseOrderRepository.findByVendorId(v.getId()).stream()
                .filter(p -> p.getStatus() != PurchaseOrder.PoStatus.CANCELLED)
                .mapToLong(p -> p.getTotalCents() == null ? 0L : p.getTotalCents())
                .sum();
        v.setTotalSpendCents(total);
        purchaseOrderRepository.findByVendorId(v.getId()).stream()
                .filter(p -> p.getStatus() != PurchaseOrder.PoStatus.CANCELLED)
                .map(PurchaseOrder::getCreatedAt)
                .max(Instant::compareTo)
                .ifPresent(v::setLastOrderedAt);
        vendorRepository.save(v);
    }

    private void validateStatusTransition(PurchaseOrder.PoStatus from, PurchaseOrder.PoStatus to) {
        if (from == to) return;
        if (from == PurchaseOrder.PoStatus.PAID && to != PurchaseOrder.PoStatus.CANCELLED) {
            throw new BadRequestException("PAID purchase orders cannot move back to " + to.name());
        }
        if (from == PurchaseOrder.PoStatus.CANCELLED) {
            throw new BadRequestException("CANCELLED purchase orders are terminal — create a new PO instead.");
        }
    }

    private String allocateReference() {
        long n = purchaseOrderRepository.nextReferenceNumber();
        int year = LocalDate.now(BUSINESS_ZONE).getYear();
        return String.format("PO-%d-%06d", year, n);
    }

    private PurchaseOrder mustFind(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PO " + id + " not found"));
    }

    /* ── mappers ── */

    private AdminPurchaseOrderSummary toSummary(PurchaseOrder p) {
        Vendor v = p.getVendor();
        Booking b = p.getBooking();
        return AdminPurchaseOrderSummary.builder()
                .id(p.getId())
                .reference(p.getReference())
                .status(p.getStatus().name().toLowerCase())
                .vendorId(v.getId())
                .vendorName(v.getName())
                .vendorCategory(v.getCategory().name().toLowerCase())
                .bookingId(b == null ? null : b.getId())
                .bookingReference(b == null ? null : b.getReference())
                .issueDate(p.getIssueDate())
                .expectedDelivery(p.getExpectedDelivery())
                .totalCents(p.getTotalCents())
                .paidCents(p.getPaidCents())
                .outstandingCents((p.getTotalCents() == null ? 0L : p.getTotalCents())
                        - (p.getPaidCents() == null ? 0L : p.getPaidCents()))
                .currency(p.getCurrency())
                .itemCount(p.getItems() == null ? 0 : p.getItems().size())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private AdminPurchaseOrderDetail toDetail(PurchaseOrder p) {
        Vendor v = p.getVendor();
        Booking b = p.getBooking();
        List<AdminPurchaseOrderDetail.Item> items = p.getItems().stream()
                .sorted(Comparator.comparing(LineItemDoc::getPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toItemDto).toList();

        return AdminPurchaseOrderDetail.builder()
                .id(p.getId())
                .reference(p.getReference())
                .status(p.getStatus().name().toLowerCase())
                .vendor(AdminPurchaseOrderDetail.VendorRef.builder()
                        .id(v.getId())
                        .name(v.getName())
                        .category(v.getCategory().name().toLowerCase())
                        .paymentTerms(v.getPaymentTerms().name().toLowerCase())
                        .primaryContactName(v.getPrimaryContactName())
                        .primaryContactPhone(v.getPrimaryContactPhone())
                        .primaryContactEmail(v.getPrimaryContactEmail())
                        .build())
                .booking(b == null ? null : AdminPurchaseOrderDetail.BookingRef.builder()
                        .id(b.getId())
                        .reference(b.getReference())
                        .eventType(b.getEventType())
                        .eventDate(b.getEventDate())
                        .build())
                .issueDate(p.getIssueDate())
                .expectedDelivery(p.getExpectedDelivery())
                .receivedAt(p.getReceivedAt())
                .subtotalCents(p.getSubtotalCents())
                .taxCents(p.getTaxCents())
                .totalCents(p.getTotalCents())
                .paidCents(p.getPaidCents())
                .currency(p.getCurrency())
                .internalNotes(p.getInternalNotes())
                .vendorNotes(p.getVendorNotes())
                .items(items)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private AdminPurchaseOrderDetail.Item toItemDto(LineItemDoc it) {
        return AdminPurchaseOrderDetail.Item.builder()
                .id(it.getId())
                .description(it.getDescription())
                .quantity(it.getQuantity())
                .unit(it.getUnit())
                .unitPriceCents(it.getUnitPriceCents())
                .lineTotalCents(it.getLineTotalCents())
                .position(it.getPosition())
                .rateCardId(it.getRateCardId())
                .notes(it.getNotes())
                .build();
    }

    private PurchaseOrder.PoStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }

    private PurchaseOrder.PoStatus parseStatusStrict(String raw) {
        try {
            return PurchaseOrder.PoStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid PO status '" + raw +
                    "'. Allowed: draft, issued, confirmed, partial, received, paid, cancelled.");
        }
    }
}
