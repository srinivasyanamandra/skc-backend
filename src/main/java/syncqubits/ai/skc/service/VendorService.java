package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.vendor.AdminVendorDetail;
import syncqubits.ai.skc.dto.vendor.AdminVendorSummary;
import syncqubits.ai.skc.dto.vendor.VendorContactRequest;
import syncqubits.ai.skc.dto.vendor.VendorContactUpdateRequest;
import syncqubits.ai.skc.dto.vendor.VendorCreateRequest;
import syncqubits.ai.skc.dto.vendor.VendorRateCardRequest;
import syncqubits.ai.skc.dto.vendor.VendorRateCardUpdateRequest;
import syncqubits.ai.skc.dto.vendor.VendorUpdateRequest;
import syncqubits.ai.skc.entity.PurchaseOrder;
import syncqubits.ai.skc.entity.Vendor;
import syncqubits.ai.skc.entity.embedded.ContactDoc;
import syncqubits.ai.skc.entity.embedded.RateCardDoc;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.PurchaseOrderRepository;
import syncqubits.ai.skc.repository.VendorRepository;

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
 * Vendor directory + per-vendor sub-collection management.
 *
 * Sub-collections (contacts, rate cards) used to live in their own tables;
 * they're now JSONB arrays on the {@link Vendor} row. The CRUD shape stays
 * identical from the API's perspective — services manipulate the in-memory
 * lists and re-save the vendor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SystemLogService systemLogService;

    /* ──────────────────────────────────── reads ─────────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<AdminVendorSummary> list(String q,
                                                 String category,
                                                 String status,
                                                 int page,
                                                 int size,
                                                 String sortField,
                                                 Sort.Direction direction) {
        Vendor.VendorCategory cat  = parseCategory(category);
        Vendor.VendorStatus   stat = parseStatus(status);
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Vendor> result = vendorRepository.searchVendors(
                q == null ? "" : q.trim(), cat, stat, PageRequest.of(page, size, sort));

        List<UUID> ids = result.getContent().stream().map(Vendor::getId).toList();

        Map<UUID, long[]> agg = new HashMap<>();
        if (!ids.isEmpty()) {
            purchaseOrderRepository.aggregateByVendorIds(ids).forEach(row ->
                    agg.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}));
        }

        return PageResponse.from(result, v -> {
            long[] vAgg = agg.getOrDefault(v.getId(), new long[]{0L, 0L});
            long outstanding = ids.isEmpty() ? 0L : purchaseOrderRepository.outstandingForVendor(v.getId());
            return toSummary(v, vAgg[0], vAgg[1], outstanding);
        });
    }

    @Transactional(readOnly = true)
    public AdminVendorDetail detail(UUID id) {
        Vendor v = mustFind(id);
        long outstanding = purchaseOrderRepository.outstandingForVendor(id);

        return AdminVendorDetail.builder()
                .id(v.getId())
                .name(v.getName())
                .category(v.getCategory().name().toLowerCase())
                .status(v.getStatus().name().toLowerCase())
                .primaryContactName(v.getPrimaryContactName())
                .primaryContactPhone(v.getPrimaryContactPhone())
                .primaryContactEmail(v.getPrimaryContactEmail())
                .gstNumber(v.getGstNumber())
                .panNumber(v.getPanNumber())
                .website(v.getWebsite())
                .addressLine1(v.getAddressLine1())
                .addressLine2(v.getAddressLine2())
                .city(v.getCity())
                .state(v.getState())
                .pincode(v.getPincode())
                .paymentTerms(v.getPaymentTerms().name().toLowerCase())
                .preferredPayment(v.getPreferredPayment())
                .currency(v.getCurrency())
                .rating(v.getRating())
                .ratingCount(v.getRatingCount())
                .totalSpendCents(v.getTotalSpendCents())
                .outstandingCents(outstanding)
                .lastOrderedAt(v.getLastOrderedAt())
                .notes(v.getNotes())
                .tags(new LinkedHashSet<>(v.getTags()))
                .contacts(v.getContacts().stream().map(this::toContactRef).toList())
                .rateCards(v.getRateCards().stream().map(this::toRateCardRef).toList())
                .recentPurchaseOrders(purchaseOrderRepository.findByVendorId(id).stream()
                        .limit(20).map(this::toPoRef).toList())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    /* ──────────────────────────────────── vendor mutation ───────────────────────── */

    @Transactional
    public AdminVendorDetail create(VendorCreateRequest req) {
        Vendor.VendorCategory category = parseCategoryStrict(req.getCategory());
        Vendor.VendorStatus status = req.getStatus() == null || req.getStatus().isBlank()
                ? Vendor.VendorStatus.ACTIVE : parseStatusStrict(req.getStatus());
        Vendor.PaymentTerms terms = req.getPaymentTerms() == null || req.getPaymentTerms().isBlank()
                ? Vendor.PaymentTerms.NET_30 : parsePaymentTerms(req.getPaymentTerms());

        Vendor v = Vendor.builder()
                .name(req.getName().trim())
                .category(category)
                .status(status)
                .primaryContactName(req.getPrimaryContactName())
                .primaryContactPhone(req.getPrimaryContactPhone())
                .primaryContactEmail(req.getPrimaryContactEmail())
                .gstNumber(req.getGstNumber())
                .panNumber(req.getPanNumber())
                .website(req.getWebsite())
                .addressLine1(req.getAddressLine1())
                .addressLine2(req.getAddressLine2())
                .city(req.getCity())
                .state(req.getState())
                .pincode(req.getPincode())
                .paymentTerms(terms)
                .preferredPayment(req.getPreferredPayment())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? "INR" : req.getCurrency())
                .notes(req.getNotes())
                .build();
        Vendor saved = vendorRepository.save(v);

        systemLogService.logEmail("vendor_created", "success", saved.getId(), "vendor",
                Map.of("name", saved.getName(), "category", saved.getCategory().name()));
        return detail(saved.getId());
    }

    @Transactional
    public AdminVendorDetail update(UUID id, VendorUpdateRequest req) {
        Vendor v = mustFind(id);
        Map<String, Object> changes = new HashMap<>();

        if (req.getName() != null) { changes.put("name", req.getName()); v.setName(req.getName()); }
        if (req.getCategory() != null) v.setCategory(parseCategoryStrict(req.getCategory()));
        if (req.getStatus() != null) {
            Vendor.VendorStatus s = parseStatusStrict(req.getStatus());
            if (s != v.getStatus()) {
                changes.put("status", Map.of("from", v.getStatus().name().toLowerCase(),
                                             "to",   s.name().toLowerCase()));
                v.setStatus(s);
            }
        }

        if (req.getPrimaryContactName()  != null) v.setPrimaryContactName(req.getPrimaryContactName());
        if (req.getPrimaryContactPhone() != null) v.setPrimaryContactPhone(req.getPrimaryContactPhone());
        if (req.getPrimaryContactEmail() != null) v.setPrimaryContactEmail(req.getPrimaryContactEmail());

        if (req.getGstNumber() != null) v.setGstNumber(req.getGstNumber());
        if (req.getPanNumber() != null) v.setPanNumber(req.getPanNumber());
        if (req.getWebsite()   != null) v.setWebsite(req.getWebsite());

        if (req.getAddressLine1() != null) v.setAddressLine1(req.getAddressLine1());
        if (req.getAddressLine2() != null) v.setAddressLine2(req.getAddressLine2());
        if (req.getCity()    != null) v.setCity(req.getCity());
        if (req.getState()   != null) v.setState(req.getState());
        if (req.getPincode() != null) v.setPincode(req.getPincode());

        if (req.getPaymentTerms()    != null) v.setPaymentTerms(parsePaymentTerms(req.getPaymentTerms()));
        if (req.getPreferredPayment()!= null) v.setPreferredPayment(req.getPreferredPayment());
        if (req.getCurrency()        != null && !req.getCurrency().isBlank()) v.setCurrency(req.getCurrency());

        if (req.getNotes() != null) v.setNotes(req.getNotes());

        Vendor saved = vendorRepository.save(v);
        if (!changes.isEmpty()) {
            systemLogService.logEmail("vendor_updated", "success", saved.getId(), "vendor", changes);
        }
        return detail(saved.getId());
    }

    @Transactional
    public void delete(UUID id) {
        Vendor v = mustFind(id);
        boolean hasOpen = purchaseOrderRepository.findByVendorId(id).stream()
                .anyMatch(p -> p.getStatus() != PurchaseOrder.PoStatus.CANCELLED);
        if (hasOpen) {
            throw new BadRequestException(
                    "Vendor has purchase orders on file. Set status to INACTIVE or BLOCKED instead.");
        }
        vendorRepository.delete(v);
        systemLogService.logEmail("vendor_deleted", "success", id, "vendor", Map.of());
    }

    /* ──────────────────────────────────── tags (TEXT[]) ─────────────────────────── */

    @Transactional
    public Set<String> setVendorTags(UUID vendorId, List<String> tags) {
        Vendor v = mustFind(vendorId);
        Set<String> normalized = normalizeTags(tags);
        v.setTags(normalized);
        vendorRepository.save(v);
        return new LinkedHashSet<>(normalized);
    }

    @Transactional(readOnly = true)
    public List<String> distinctVendorTags() {
        return vendorRepository.distinctTags();
    }

    /* ──────────────────────────────────── contacts (JSONB) ──────────────────────── */

    @Transactional
    public AdminVendorDetail.ContactRef addContact(UUID vendorId, VendorContactRequest req) {
        Vendor v = mustFind(vendorId);
        boolean primary = Boolean.TRUE.equals(req.getPrimary());
        if (primary) {
            v.getContacts().forEach(c -> c.setPrimary(false));
        }
        ContactDoc c = ContactDoc.builder()
                .id(UUID.randomUUID())
                .name(req.getName())
                .role(req.getRole())
                .phone(req.getPhone())
                .email(req.getEmail())
                .primary(primary)
                .notes(req.getNotes())
                .createdAt(java.time.Instant.now())
                .build();
        v.getContacts().add(c);
        vendorRepository.save(v);
        return toContactRef(c);
    }

    @Transactional
    public AdminVendorDetail.ContactRef updateContact(UUID vendorId, UUID contactId, VendorContactUpdateRequest req) {
        Vendor v = mustFind(vendorId);
        ContactDoc c = v.getContacts().stream()
                .filter(x -> contactId.equals(x.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Contact " + contactId + " not found"));

        if (req.getName()  != null) c.setName(req.getName());
        if (req.getRole()  != null) c.setRole(req.getRole());
        if (req.getPhone() != null) c.setPhone(req.getPhone());
        if (req.getEmail() != null) c.setEmail(req.getEmail());
        if (req.getNotes() != null) c.setNotes(req.getNotes());
        if (Boolean.TRUE.equals(req.getPrimary()) && !Boolean.TRUE.equals(c.getPrimary())) {
            v.getContacts().forEach(x -> x.setPrimary(contactId.equals(x.getId())));
        }
        vendorRepository.save(v);
        return toContactRef(c);
    }

    @Transactional
    public void deleteContact(UUID vendorId, UUID contactId) {
        Vendor v = mustFind(vendorId);
        boolean removed = v.getContacts().removeIf(c -> contactId.equals(c.getId()));
        if (!removed) throw new ResourceNotFoundException("Contact " + contactId + " not found");
        vendorRepository.save(v);
    }

    /* ──────────────────────────────────── rate cards (JSONB) ────────────────────── */

    @Transactional
    public AdminVendorDetail.RateCardRef addRateCard(UUID vendorId, VendorRateCardRequest req) {
        Vendor v = mustFind(vendorId);
        RateCardDoc r = RateCardDoc.builder()
                .id(UUID.randomUUID())
                .itemName(req.getItemName())
                .unit(req.getUnit() == null || req.getUnit().isBlank() ? "each" : req.getUnit())
                .unitPriceCents(req.getUnitPriceCents() == null ? 0L : req.getUnitPriceCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? v.getCurrency() : req.getCurrency())
                .validFrom(req.getValidFrom())
                .validTo(req.getValidTo())
                .notes(req.getNotes())
                .active(req.getActive() == null ? Boolean.TRUE : req.getActive())
                .build();
        v.getRateCards().add(r);
        vendorRepository.save(v);
        return toRateCardRef(r);
    }

    @Transactional
    public AdminVendorDetail.RateCardRef updateRateCard(UUID vendorId, UUID cardId, VendorRateCardUpdateRequest req) {
        Vendor v = mustFind(vendorId);
        RateCardDoc r = v.getRateCards().stream()
                .filter(x -> cardId.equals(x.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Rate card " + cardId + " not found"));

        if (req.getItemName()       != null) r.setItemName(req.getItemName());
        if (req.getUnit()           != null) r.setUnit(req.getUnit());
        if (req.getUnitPriceCents() != null) r.setUnitPriceCents(req.getUnitPriceCents());
        if (req.getCurrency()       != null && !req.getCurrency().isBlank()) r.setCurrency(req.getCurrency());
        if (req.getValidFrom()      != null) r.setValidFrom(req.getValidFrom());
        if (req.getValidTo()        != null) r.setValidTo(req.getValidTo());
        if (req.getNotes()          != null) r.setNotes(req.getNotes());
        if (req.getActive()         != null) r.setActive(req.getActive());
        vendorRepository.save(v);
        return toRateCardRef(r);
    }

    @Transactional
    public void deleteRateCard(UUID vendorId, UUID cardId) {
        Vendor v = mustFind(vendorId);
        boolean removed = v.getRateCards().removeIf(c -> cardId.equals(c.getId()));
        if (!removed) throw new ResourceNotFoundException("Rate card " + cardId + " not found");
        vendorRepository.save(v);
    }

    /* ──────────────────────────────────── helpers ───────────────────────────────── */

    private Vendor mustFind(UUID id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor " + id + " not found"));
    }

    private static Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return new LinkedHashSet<>();
        return tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AdminVendorSummary toSummary(Vendor v, long poCount, long totalSpend, long outstanding) {
        return AdminVendorSummary.builder()
                .id(v.getId())
                .name(v.getName())
                .category(v.getCategory().name().toLowerCase())
                .status(v.getStatus().name().toLowerCase())
                .primaryContactName(v.getPrimaryContactName())
                .primaryContactPhone(v.getPrimaryContactPhone())
                .primaryContactEmail(v.getPrimaryContactEmail())
                .city(v.getCity())
                .state(v.getState())
                .paymentTerms(v.getPaymentTerms().name().toLowerCase())
                .currency(v.getCurrency())
                .rating(v.getRating())
                .ratingCount(v.getRatingCount())
                .totalSpendCents(totalSpend)
                .outstandingCents(outstanding)
                .poCount(poCount)
                .lastOrderedAt(v.getLastOrderedAt())
                .tags(new LinkedHashSet<>(v.getTags()))
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    private AdminVendorDetail.ContactRef toContactRef(ContactDoc c) {
        return AdminVendorDetail.ContactRef.builder()
                .id(c.getId())
                .name(c.getName())
                .role(c.getRole())
                .phone(c.getPhone())
                .email(c.getEmail())
                .primary(c.getPrimary())
                .notes(c.getNotes())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private AdminVendorDetail.RateCardRef toRateCardRef(RateCardDoc r) {
        return AdminVendorDetail.RateCardRef.builder()
                .id(r.getId())
                .itemName(r.getItemName())
                .unit(r.getUnit())
                .unitPriceCents(r.getUnitPriceCents())
                .currency(r.getCurrency())
                .validFrom(r.getValidFrom())
                .validTo(r.getValidTo())
                .active(r.getActive())
                .notes(r.getNotes())
                .build();
    }

    private AdminVendorDetail.PoRef toPoRef(PurchaseOrder p) {
        return AdminVendorDetail.PoRef.builder()
                .id(p.getId())
                .reference(p.getReference())
                .status(p.getStatus().name().toLowerCase())
                .issueDate(p.getIssueDate())
                .expectedDelivery(p.getExpectedDelivery())
                .totalCents(p.getTotalCents())
                .paidCents(p.getPaidCents())
                .currency(p.getCurrency())
                .bookingId(p.getBooking() == null ? null : p.getBooking().getId())
                .bookingReference(p.getBooking() == null ? null : p.getBooking().getReference())
                .createdAt(p.getCreatedAt())
                .build();
    }

    /* ── parsers ── */

    private Vendor.VendorCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseCategoryStrict(raw);
    }
    private Vendor.VendorCategory parseCategoryStrict(String raw) {
        try { return Vendor.VendorCategory.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Invalid category '" + raw + "'."); }
    }
    private Vendor.VendorStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseStatusStrict(raw);
    }
    private Vendor.VendorStatus parseStatusStrict(String raw) {
        try { return Vendor.VendorStatus.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BadRequestException(
                "Invalid status '" + raw + "'. Allowed: active, inactive, blocked."); }
    }
    private Vendor.PaymentTerms parsePaymentTerms(String raw) {
        try { return Vendor.PaymentTerms.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BadRequestException(
                "Invalid payment terms '" + raw + "'."); }
    }
}
