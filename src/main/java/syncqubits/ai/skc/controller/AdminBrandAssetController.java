package syncqubits.ai.skc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import syncqubits.ai.skc.dto.asset.BrandAssetResponse;
import syncqubits.ai.skc.service.BrandAssetService;

import java.util.List;
import java.util.UUID;

/**
 * Brand asset CRUD for the Document Studio.
 *
 * <p>List + upload + delete only — no rename/update endpoint by design.
 * Renames would invalidate any cached PDF references; the safer pattern is
 * "delete and re-upload" so the URL changes too.
 *
 * <p>Auth is enforced globally by {@code SecurityConfig} via the
 * {@code /api/admin/**} matcher; the {@code /uploads/**} static path that
 * actually serves the bytes is intentionally permitAll so {@code <img>}
 * tags work without a JWT.
 */
@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
@Slf4j
public class AdminBrandAssetController {

    private final BrandAssetService service;

    @GetMapping
    public ResponseEntity<List<BrandAssetResponse>> list(
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(service.list(role));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<BrandAssetResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "altText", required = false) String altText) {
        return ResponseEntity.ok(service.upload(file, role, altText));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
