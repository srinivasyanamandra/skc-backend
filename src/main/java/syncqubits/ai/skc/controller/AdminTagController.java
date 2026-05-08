package syncqubits.ai.skc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.service.AdminClientService;
import syncqubits.ai.skc.service.VendorService;

import java.util.List;

/**
 * Tag autocomplete. After consolidation, tags are TEXT[] columns on
 * {@code clients} and {@code vendors} — there's no registry. This
 * endpoint returns the distinct tag values currently in use, scoped by
 * subject, so the tag-picker UI can autocomplete.
 *
 * No POST/DELETE endpoints — assignment is what creates a tag, removing
 * the last assignment is what removes it.
 */
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final AdminClientService adminClientService;
    private final VendorService vendorService;

    @GetMapping
    public ResponseEntity<List<String>> list(
            @RequestParam(required = false, defaultValue = "client") String scope) {
        return switch (scope.trim().toLowerCase()) {
            case "client" -> ResponseEntity.ok(adminClientService.distinctClientTags());
            case "vendor" -> ResponseEntity.ok(vendorService.distinctVendorTags());
            default -> throw new BadRequestException("Invalid scope '" + scope + "'. Allowed: client, vendor.");
        };
    }
}
