package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.entity.SystemLog;
import syncqubits.ai.skc.repository.SystemLogRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuth(String action, String status, String email, String ipAddress, String userAgent) {
        try {
            SystemLog log = SystemLog.builder()
                    .type("auth")
                    .action(action)
                    .status(status)
                    .details(Map.of("email", email))
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            systemLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save auth log", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmail(String action, String status, UUID entityId, String entityType, Map<String, Object> details) {
        try {
            SystemLog log = SystemLog.builder()
                    .type("email")
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .status(status)
                    .details(details)
                    .build();
            systemLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save email log", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logReview(String action, String status, UUID reviewId, Map<String, Object> details) {
        try {
            SystemLog log = SystemLog.builder()
                    .type("review")
                    .entityType("review")
                    .entityId(reviewId)
                    .action(action)
                    .status(status)
                    .details(details)
                    .build();
            systemLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save review log", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logQuote(String action, String status, UUID quoteId, Map<String, Object> details) {
        try {
            SystemLog log = SystemLog.builder()
                    .type("quote")
                    .entityType("quote_request")
                    .entityId(quoteId)
                    .action(action)
                    .status(status)
                    .details(details)
                    .build();
            systemLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save quote log", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCampaign(String action, String status, UUID campaignId, Map<String, Object> details) {
        try {
            SystemLog log = SystemLog.builder()
                    .type("campaign")
                    .entityType("email_campaign")
                    .entityId(campaignId)
                    .action(action)
                    .status(status)
                    .details(details)
                    .build();
            systemLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save campaign log", e);
        }
    }
}
