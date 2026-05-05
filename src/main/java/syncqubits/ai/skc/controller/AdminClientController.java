package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.client.AdminClientDetail;
import syncqubits.ai.skc.dto.client.AdminClientSummary;
import syncqubits.ai.skc.dto.client.ClientUpdateRequest;
import syncqubits.ai.skc.service.AdminClientService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@Slf4j
public class AdminClientController {

    private final AdminClientService adminClientService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminClientSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(adminClientService.list(q, status, since, until, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminClientDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminClientService.detail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminClientDetail> update(@PathVariable UUID id,
                                                     @Valid @RequestBody ClientUpdateRequest body) {
        return ResponseEntity.ok(adminClientService.update(id, body));
    }
}
