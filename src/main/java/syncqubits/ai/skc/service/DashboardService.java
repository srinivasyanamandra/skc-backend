package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.repository.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final ReviewRepository reviewRepository;
    private final SubscriberRepository subscriberRepository;
    private final EmailCampaignRepository emailCampaignRepository;
    private final SystemLogRepository systemLogRepository;

    public Map<String, Object> getDashboardData() {
        log.info("Fetching dashboard data");

        Instant monthStart = Instant.now().minus(30, ChronoUnit.DAYS);

        // Totals
        Map<String, Object> totals = Map.of(
                "clients", clientRepository.count(),
                "quotesPending", quoteRequestRepository.countByStatus(QuoteRequest.QuoteStatus.PENDING),
                "quotesBooked", quoteRequestRepository.countByStatus(QuoteRequest.QuoteStatus.BOOKED),
                "reviewsPending", reviewRepository.countByTypeAndStatus(Review.ReviewType.REVIEW, Review.ReviewStatus.PENDING),
                "reviewsApproved", reviewRepository.countByTypeAndStatus(Review.ReviewType.REVIEW, Review.ReviewStatus.APPROVED),
                "subscribersActive", subscriberRepository.countByIsActive(true),
                "campaignsThisMonth", emailCampaignRepository.countCreatedSince(monthStart)
        );

        // Trends
        Map<String, Object> trends = Map.of(
                "newClientsThisMonth", clientRepository.searchClients("", null, monthStart, null, PageRequest.of(0, 1)).getTotalElements(),
                "newQuotesThisMonth", quoteRequestRepository.countCreatedSince(monthStart),
                "campaignsSentThisMonth", emailCampaignRepository.countCreatedSince(monthStart),
                "emailsDeliveredThisMonth", emailCampaignRepository.sumSentCountSince(monthStart)
        );

        // Recent activity
        var recentLogs = systemLogRepository.findRecentActivity(PageRequest.of(0, 10));
        List<Map<String, Object>> recentActivity = recentLogs.getContent().stream()
                .map(log -> {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("id", log.getId().toString());
                    activity.put("type", log.getType());
                    activity.put("action", log.getAction());
                    activity.put("message", buildLogMessage(log.getType(), log.getAction(), log.getDetails()));
                    activity.put("at", log.getCreatedAt().toString());
                    return activity;
                })
                .collect(Collectors.toList());

        return Map.of(
                "totals", totals,
                "trends", trends,
                "recentActivity", recentActivity
        );
    }

    private String buildLogMessage(String type, String action, Map<String, Object> details) {
        if (details == null) {
            return String.format("%s %s", type, action);
        }
        
        return switch (type) {
            case "campaign" -> String.format("Campaign '%s' %s", 
                    details.getOrDefault("campaignName", "Unknown"), action);
            case "email" -> String.format("Email %s to %s", 
                    action, details.getOrDefault("recipientEmail", "recipient"));
            case "review" -> String.format("Review %s by %s", 
                    action, details.getOrDefault("reviewerName", "user"));
            case "quote" -> String.format("Quote %s from %s", 
                    action, details.getOrDefault("clientEmail", "client"));
            default -> String.format("%s %s", type, action);
        };
    }
}
