package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.po.AdminPurchaseOrderDetail;
import syncqubits.ai.skc.dto.po.AdminPurchaseOrderSummary;
import syncqubits.ai.skc.dto.po.PurchaseOrderCreateRequest;
import syncqubits.ai.skc.dto.po.PurchaseOrderUpdateRequest;
import syncqubits.ai.skc.service.PurchaseOrderService;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/purchase-orders")
@RequiredArgsConstructor
@Slf4j
public class AdminPurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminPurchaseOrderSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(purchaseOrderService.list(q, status, vendorId, bookingId, fromDate, toDate, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminPurchaseOrderDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.detail(id));
    }

    @PostMapping
    public ResponseEntity<AdminPurchaseOrderDetail> create(@Valid @RequestBody PurchaseOrderCreateRequest body) {
        return ResponseEntity.ok(purchaseOrderService.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminPurchaseOrderDetail> update(@PathVariable UUID id,
                                                           @Valid @RequestBody PurchaseOrderUpdateRequest body) {
        return ResponseEntity.ok(purchaseOrderService.update(id, body));
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<AdminPurchaseOrderDetail> recordPayment(@PathVariable UUID id,
                                                                  @RequestBody Map<String, Long> body) {
        long paid = body.getOrDefault("paidCents", 0L);
        return ResponseEntity.ok(purchaseOrderService.recordPayment(id, paid));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
