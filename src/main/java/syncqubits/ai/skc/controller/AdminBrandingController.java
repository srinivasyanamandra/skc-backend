package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import syncqubits.ai.skc.dto.branding.BrandingProfileResponse;
import syncqubits.ai.skc.dto.branding.BrandingProfileUpdateRequest;
import syncqubits.ai.skc.service.BrandingProfileService;

/**
 * Branding profile endpoints for the Document Studio.
 *
 * <p>Singleton — there is exactly one profile per deployment. We
 * intentionally expose GET + PUT only (no POST, DELETE, or list) so callers
 * can't fork the brand by accident. Auth is enforced globally by
 * {@code SecurityConfig} via the {@code /api/admin/**} matcher.
 */
@RestController
@RequestMapping("/api/admin/branding")
@RequiredArgsConstructor
@Slf4j
public class AdminBrandingController {

    private final BrandingProfileService service;

    @GetMapping
    public ResponseEntity<BrandingProfileResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<BrandingProfileResponse> update(
            @Valid @RequestBody BrandingProfileUpdateRequest body) {
        return ResponseEntity.ok(service.update(body));
    }
}
