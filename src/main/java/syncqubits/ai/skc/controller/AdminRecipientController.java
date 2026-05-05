package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.recipient.RecipientSearchResponse;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;
import syncqubits.ai.skc.dto.recipient.ResolveResponse;
import syncqubits.ai.skc.service.RecipientResolver;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/recipients")
@RequiredArgsConstructor
@Slf4j
public class AdminRecipientController {

    private final RecipientResolver recipientResolver;

    @GetMapping
    public ResponseEntity<RecipientSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(required = false) String clientStatus,
            @RequestParam(required = false) Boolean subscribed,
            @RequestParam(required = false) Short minRating,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(recipientResolver.search(
                q, kind, clientStatus, subscribed, minRating, eventType, since, until,
                page, size, sortField, dir));
    }

    @PostMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@Valid @RequestBody ResolveRequest req) {
        return ResponseEntity.ok(recipientResolver.toResponse(recipientResolver.materialise(req)));
    }
}
