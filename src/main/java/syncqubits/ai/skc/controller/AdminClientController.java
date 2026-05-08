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
import syncqubits.ai.skc.dto.client.ClientAddressRequest;
import syncqubits.ai.skc.dto.client.ClientAddressResponse;
import syncqubits.ai.skc.dto.client.ClientCreateRequest;
import syncqubits.ai.skc.dto.client.ClientNoteRequest;
import syncqubits.ai.skc.dto.client.ClientNoteResponse;
import syncqubits.ai.skc.dto.client.ClientUpdateRequest;
import syncqubits.ai.skc.service.AdminClientService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @PostMapping
    public ResponseEntity<AdminClientDetail> create(@Valid @RequestBody ClientCreateRequest body) {
        return ResponseEntity.ok(adminClientService.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminClientDetail> update(@PathVariable UUID id,
                                                     @Valid @RequestBody ClientUpdateRequest body) {
        return ResponseEntity.ok(adminClientService.update(id, body));
    }

    /** Mark "we just spoke to this client" without changing anything else.
     *  Drives the dashboard's "needs follow-up" list. */
    @PostMapping("/{id}/touch")
    public ResponseEntity<AdminClientDetail> touch(@PathVariable UUID id) {
        return ResponseEntity.ok(adminClientService.touchLastContacted(id));
    }

    /* ─────────────────────────── notes (CRM timeline) ──────────────────────────── */

    @PostMapping("/{id}/notes")
    public ResponseEntity<ClientNoteResponse> addNote(@PathVariable UUID id,
                                                      @Valid @RequestBody ClientNoteRequest body) {
        return ResponseEntity.ok(adminClientService.addNote(id, body));
    }

    @PutMapping("/{id}/notes/{noteId}")
    public ResponseEntity<ClientNoteResponse> updateNote(@PathVariable UUID id,
                                                         @PathVariable UUID noteId,
                                                         @Valid @RequestBody ClientNoteRequest body) {
        return ResponseEntity.ok(adminClientService.updateNote(id, noteId, body));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        adminClientService.deleteNote(id, noteId);
        return ResponseEntity.noContent().build();
    }

    /* ──────────────────────────────── addresses ────────────────────────────────── */

    @PostMapping("/{id}/addresses")
    public ResponseEntity<ClientAddressResponse> addAddress(@PathVariable UUID id,
                                                            @Valid @RequestBody ClientAddressRequest body) {
        return ResponseEntity.ok(adminClientService.addAddress(id, body));
    }

    @PutMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<ClientAddressResponse> updateAddress(@PathVariable UUID id,
                                                               @PathVariable UUID addressId,
                                                               @Valid @RequestBody ClientAddressRequest body) {
        return ResponseEntity.ok(adminClientService.updateAddress(id, addressId, body));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID id, @PathVariable UUID addressId) {
        adminClientService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    /* ──────────────────────────────── tags ─────────────────────────────────────── */

    /** Set the entire tag set on a client (idempotent). Pass an empty list
     *  to clear. Tags are normalized to lowercase server-side. */
    @PutMapping("/{id}/tags")
    public ResponseEntity<Set<String>> setTags(@PathVariable UUID id,
                                               @RequestBody Map<String, List<String>> body) {
        return ResponseEntity.ok(adminClientService.setClientTags(id, body.getOrDefault("tags", List.of())));
    }
}
