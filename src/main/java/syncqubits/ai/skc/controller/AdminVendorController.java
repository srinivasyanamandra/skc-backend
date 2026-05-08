package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.vendor.AdminVendorDetail;
import syncqubits.ai.skc.dto.vendor.AdminVendorSummary;
import syncqubits.ai.skc.dto.vendor.VendorContactRequest;
import syncqubits.ai.skc.dto.vendor.VendorContactUpdateRequest;
import syncqubits.ai.skc.dto.vendor.VendorCreateRequest;
import syncqubits.ai.skc.dto.vendor.VendorRateCardRequest;
import syncqubits.ai.skc.dto.vendor.VendorRateCardUpdateRequest;
import syncqubits.ai.skc.dto.vendor.VendorUpdateRequest;
import syncqubits.ai.skc.service.VendorService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
@Slf4j
public class AdminVendorController {

    private final VendorService vendorService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminVendorSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(vendorService.list(q, category, status, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminVendorDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(vendorService.detail(id));
    }

    @PostMapping
    public ResponseEntity<AdminVendorDetail> create(@Valid @RequestBody VendorCreateRequest body) {
        return ResponseEntity.ok(vendorService.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminVendorDetail> update(@PathVariable UUID id,
                                                    @Valid @RequestBody VendorUpdateRequest body) {
        return ResponseEntity.ok(vendorService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vendorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /* ─────────────────────────────────── tags ───────────────────────────────────── */

    @PutMapping("/{id}/tags")
    public ResponseEntity<Set<String>> setTags(@PathVariable UUID id,
                                               @RequestBody Map<String, List<String>> body) {
        return ResponseEntity.ok(vendorService.setVendorTags(id, body.getOrDefault("tags", List.of())));
    }

    /* ─────────────────────────────────── contacts ──────────────────────────────── */

    @PostMapping("/{id}/contacts")
    public ResponseEntity<AdminVendorDetail.ContactRef> addContact(@PathVariable UUID id,
                                                                   @Valid @RequestBody VendorContactRequest body) {
        return ResponseEntity.ok(vendorService.addContact(id, body));
    }

    @PutMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<AdminVendorDetail.ContactRef> updateContact(@PathVariable UUID id,
                                                                      @PathVariable UUID contactId,
                                                                      @Valid @RequestBody VendorContactUpdateRequest body) {
        return ResponseEntity.ok(vendorService.updateContact(id, contactId, body));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID id, @PathVariable UUID contactId) {
        vendorService.deleteContact(id, contactId);
        return ResponseEntity.noContent().build();
    }

    /* ─────────────────────────────────── rate cards ────────────────────────────── */

    @PostMapping("/{id}/rate-cards")
    public ResponseEntity<AdminVendorDetail.RateCardRef> addRateCard(@PathVariable UUID id,
                                                                     @Valid @RequestBody VendorRateCardRequest body) {
        return ResponseEntity.ok(vendorService.addRateCard(id, body));
    }

    @PutMapping("/{id}/rate-cards/{cardId}")
    public ResponseEntity<AdminVendorDetail.RateCardRef> updateRateCard(@PathVariable UUID id,
                                                                        @PathVariable UUID cardId,
                                                                        @Valid @RequestBody VendorRateCardUpdateRequest body) {
        return ResponseEntity.ok(vendorService.updateRateCard(id, cardId, body));
    }

    @DeleteMapping("/{id}/rate-cards/{cardId}")
    public ResponseEntity<Void> deleteRateCard(@PathVariable UUID id, @PathVariable UUID cardId) {
        vendorService.deleteRateCard(id, cardId);
        return ResponseEntity.noContent().build();
    }
}
