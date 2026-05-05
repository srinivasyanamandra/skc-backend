package syncqubits.ai.skc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.subscriber.AdminSubscriberSummary;
import syncqubits.ai.skc.service.AdminSubscriberService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscribers")
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriberController {

    private final AdminSubscriberService adminSubscriberService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminSubscriberSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(adminSubscriberService.list(q, active, since, until, page, size, sortField, direction));
    }

    @PutMapping("/{id}/unsubscribe")
    public ResponseEntity<AdminSubscriberSummary> unsubscribe(@PathVariable UUID id) {
        return ResponseEntity.ok(adminSubscriberService.unsubscribe(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        adminSubscriberService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id.toString()));
    }
}
