package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.campaign.*;
import syncqubits.ai.skc.service.CampaignService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
@Slf4j
public class AdminCampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<PageResponse<CampaignSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(campaignService.list(status, q, page, size, sortField, dir));
    }

    @PostMapping
    public ResponseEntity<CampaignSummary> create(@Valid @RequestBody CampaignCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(body));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int recipientPage,
            @RequestParam(defaultValue = "50") int recipientSize) {
        return ResponseEntity.ok(campaignService.detail(id, recipientPage, recipientSize));
    }

    @PostMapping("/{id}/recipients")
    public ResponseEntity<Map<String, Object>> setRecipients(@PathVariable UUID id,
                                                              @RequestBody CampaignRecipientsRequest body) {
        return ResponseEntity.ok(campaignService.setRecipients(id, body));
    }

    @PutMapping("/{id}/templates")
    public ResponseEntity<Map<String, Object>> assignTemplates(@PathVariable UUID id,
                                                                @Valid @RequestBody CampaignTemplatesRequest body) {
        return ResponseEntity.ok(campaignService.assignTemplates(id, body));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> preview(@PathVariable UUID id,
                                                        @RequestBody(required = false) CampaignPreviewRequest body) {
        CampaignPreviewRequest req = body == null
                ? CampaignPreviewRequest.builder().sample(1).build() : body;
        return ResponseEntity.ok(campaignService.preview(id, req));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, Object>> send(@PathVariable UUID id,
                                                     @RequestBody(required = false) CampaignSendRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(campaignService.send(id, body));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.cancel(id));
    }
}
