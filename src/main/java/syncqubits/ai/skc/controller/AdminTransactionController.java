package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.transaction.AdminTransactionSummary;
import syncqubits.ai.skc.dto.transaction.TransactionCreateRequest;
import syncqubits.ai.skc.dto.transaction.TransactionUpdateRequest;
import syncqubits.ai.skc.service.TransactionService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified cash-flow ledger. {@code direction=incoming} represents money
 * received from a client; {@code direction=outgoing} represents money
 * paid to a vendor or for an expense category.
 */
@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
@Slf4j
public class AdminTransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminTransactionSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "paidAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(transactionService.list(
                q, direction, status, method, category,
                bookingId, invoiceId, vendorId, purchaseOrderId, clientId,
                fromDate, toDate, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminTransactionSummary> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.detail(id));
    }

    @PostMapping
    public ResponseEntity<AdminTransactionSummary> record(@Valid @RequestBody TransactionCreateRequest body) {
        return ResponseEntity.ok(transactionService.record(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminTransactionSummary> update(@PathVariable UUID id,
                                                          @Valid @RequestBody TransactionUpdateRequest body) {
        return ResponseEntity.ok(transactionService.update(id, body));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<AdminTransactionSummary> refund(@PathVariable UUID id,
                                                          @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(transactionService.refund(id, reason));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
