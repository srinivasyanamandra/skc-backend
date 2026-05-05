package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.template.TemplateRequest;
import syncqubits.ai.skc.dto.template.TemplateResponse;
import syncqubits.ai.skc.dto.template.TemplateTestRequest;
import syncqubits.ai.skc.service.TemplateService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/templates")
@RequiredArgsConstructor
@Slf4j
public class AdminTemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<PageResponse<TemplateResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "updatedAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(templateService.list(q, type, isActive, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.get(id));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody TemplateRequest body) {
        return ResponseEntity.ok(templateService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id.toString()));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable UUID id,
                                                     @Valid @RequestBody TemplateTestRequest body) {
        return ResponseEntity.ok(templateService.testSend(id, body));
    }
}
