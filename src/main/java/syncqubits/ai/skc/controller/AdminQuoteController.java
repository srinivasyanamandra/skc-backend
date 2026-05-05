package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.quote.AdminQuoteDetail;
import syncqubits.ai.skc.dto.quote.AdminQuoteSummary;
import syncqubits.ai.skc.dto.quote.QuoteStatusUpdateRequest;
import syncqubits.ai.skc.service.AdminQuoteService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quotes")
@RequiredArgsConstructor
@Slf4j
public class AdminQuoteController {

    private final AdminQuoteService adminQuoteService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminQuoteSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(adminQuoteService.list(status, eventType, fromDate, toDate, page, size, sortField, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminQuoteDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminQuoteService.detail(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AdminQuoteDetail> updateStatus(@PathVariable UUID id,
                                                          @Valid @RequestBody QuoteStatusUpdateRequest body) {
        return ResponseEntity.ok(adminQuoteService.updateStatus(id, body));
    }
}
