package syncqubits.ai.skc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import syncqubits.ai.skc.config.AppProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug endpoint to verify configuration loading.
 * Only enabled in non-production environments.
 * Access: GET /api/debug/config
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "debug.endpoints.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigDebugController {

    private final AppProperties appProperties;

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        var adminEmails = appProperties.getAdmin().getEmails();
        var adminPasswords = appProperties.getAdmin().getPasswords();
        
        config.put("adminEmailsCount", adminEmails != null ? adminEmails.size() : 0);
        config.put("adminPasswordsCount", adminPasswords != null ? adminPasswords.size() : 0);
        config.put("adminEmails", adminEmails);
        
        // Mask passwords for security - only show if they're set
        if (adminPasswords != null) {
            config.put("adminPasswordsSet", adminPasswords.stream()
                    .map(p -> p != null && !p.isBlank() ? "***SET***" : "***EMPTY***")
                    .toList());
        }
        
        config.put("listsMatch", adminEmails != null && adminPasswords != null 
                && adminEmails.size() == adminPasswords.size());
        
        return config;
    }
}
