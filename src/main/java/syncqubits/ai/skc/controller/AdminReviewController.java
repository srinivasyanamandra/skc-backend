package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.review.*;
import syncqubits.ai.skc.service.AdminReviewService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@Slf4j
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminReviewSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short minRating,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(adminReviewService.listReviews(
                status, minRating, isPublic, isFeatured, q, page, size, sortField, dir));
    }

    @GetMapping("/invitations")
    public ResponseEntity<PageResponse<AdminInvitationSummary>> listInvitations(
            @RequestParam(required = false) Boolean used,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(adminReviewService.listInvitations(used, q, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminReviewService.detail(id));
    }

    @PostMapping("/invite")
    public ResponseEntity<InviteResponse> invite(@Valid @RequestBody InviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminReviewService.invite(req));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<AdminReviewSummary> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(adminReviewService.approve(id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<AdminReviewSummary> reject(@PathVariable UUID id,
                                                      @RequestBody(required = false) @Valid RejectRequest body) {
        String reason = body == null ? null : body.getReason();
        return ResponseEntity.ok(adminReviewService.reject(id, reason));
    }

    @PutMapping("/{id}/feature")
    public ResponseEntity<AdminReviewSummary> feature(@PathVariable UUID id,
                                                       @RequestBody(required = false) FeatureRequest body) {
        Boolean explicit = body == null ? null : body.getFeatured();
        return ResponseEntity.ok(adminReviewService.toggleFeatured(id, explicit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        adminReviewService.softDelete(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id.toString()));
    }
}
