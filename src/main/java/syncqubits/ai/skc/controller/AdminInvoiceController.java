package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.invoice.AdminInvoiceDetail;
import syncqubits.ai.skc.dto.invoice.AdminInvoiceSummary;
import syncqubits.ai.skc.dto.invoice.InvoiceCreateRequest;
import syncqubits.ai.skc.dto.invoice.InvoiceUpdateRequest;
import syncqubits.ai.skc.service.InvoiceService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@Slf4j
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminInvoiceSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(invoiceService.list(q, status, bookingId, clientId, fromDate, toDate, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminInvoiceDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.detail(id));
    }

    @PostMapping
    public ResponseEntity<AdminInvoiceDetail> create(@Valid @RequestBody InvoiceCreateRequest body) {
        return ResponseEntity.ok(invoiceService.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminInvoiceDetail> update(@PathVariable UUID id,
                                                     @Valid @RequestBody InvoiceUpdateRequest body) {
        return ResponseEntity.ok(invoiceService.update(id, body));
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<AdminInvoiceDetail> markIssued(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markIssued(id));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<AdminInvoiceDetail> voidInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.voidInvoice(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
