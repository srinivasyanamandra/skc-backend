package syncqubits.ai.skc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs configuration on application startup for debugging purposes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationLogger {

    private final AppProperties appProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        var adminEmails = appProperties.getAdmin().getEmails();
        var adminPasswords = appProperties.getAdmin().getPasswords();
        
        log.info("=== Admin Configuration ===");
        log.info("Admin emails count: {}", adminEmails != null ? adminEmails.size() : 0);
        log.info("Admin passwords count: {}", adminPasswords != null ? adminPasswords.size() : 0);
        
        if (adminEmails != null) {
            for (int i = 0; i < adminEmails.size(); i++) {
                log.info("Admin[{}]: email={}", i, adminEmails.get(i));
            }
        }
        
        if (adminPasswords != null) {
            for (int i = 0; i < adminPasswords.size(); i++) {
                String pwd = adminPasswords.get(i);
                if (pwd != null && !pwd.isBlank()) {
                    log.info("Admin[{}]: password={}... (length: {})", 
                            i, 
                            pwd.substring(0, Math.min(3, pwd.length())),
                            pwd.length());
                } else {
                    log.warn("Admin[{}]: password is NULL or BLANK", i);
                }
            }
        }
        
        log.info("Lists match: {}", adminEmails != null && adminPasswords != null 
                && adminEmails.size() == adminPasswords.size());
        log.info("===========================");
    }
}
