package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import syncqubits.ai.skc.dto.review.ReviewSubmitRequest;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.repository.ReviewRepository;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final SystemLogService systemLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> getReviewInvitation(String token) {
        log.info("Fetching review invitation for token: {}", token);

        Review invitation = reviewRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "EXPIRED_OR_USED"));

        // Check if invitation is valid
        if (invitation.getType() != Review.ReviewType.INVITATION) {
            throw new ResponseStatusException(HttpStatus.GONE, "EXPIRED_OR_USED");
        }

        if (invitation.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "EXPIRED_OR_USED");
        }

        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "EXPIRED_OR_USED");
        }

        return Map.of(
                "valid", true,
                "client", Map.of("name", invitation.getClient().getName()),
                "eventType", invitation.getEventType(),
                "eventDate", invitation.getEventDate().toString(),
                "expiresAt", invitation.getExpiresAt().toString()
        );
    }

    @Transactional
    public Map<String, Object> submitReview(String token, ReviewSubmitRequest request) {
        log.info("Submitting review for token: {}", token);

        // Lock the invitation row for update
        Review invitation = reviewRepository.findValidInvitationByTokenForUpdate(token, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "EXPIRED_OR_USED"));

        // Create the review
        Review review = Review.builder()
                .client(invitation.getClient())
                .type(Review.ReviewType.REVIEW)
                .eventType(invitation.getEventType())
                .eventDate(invitation.getEventDate())
                .invitation(invitation)
                .reviewerName(request.getReviewerName())
                .overallRating(request.getOverallRating())
                .foodQualityRating(request.getFoodQualityRating())
                .tasteRating(request.getTasteRating())
                .presentationRating(request.getPresentationRating())
                .staffBehaviorRating(request.getStaffBehaviorRating())
                .timelinessRating(request.getTimelinessRating())
                .serviceQualityRating(request.getServiceQualityRating())
                .comments(request.getComments())
                .suggestions(request.getSuggestions())
                .recommend(request.getRecommend())
                .status(Review.ReviewStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        review = reviewRepository.save(review);

        // Mark invitation as used
        invitation.setUsedAt(Instant.now());
        reviewRepository.save(invitation);

        // Log the review submission
        systemLogService.logReview(
                "submitted",
                "success",
                review.getId(),
                Map.of(
                        "reviewerName", request.getReviewerName(),
                        "overallRating", request.getOverallRating(),
                        "invitationId", invitation.getId().toString()
                )
        );

        log.info("Review submitted with ID: {}", review.getId());

        return Map.of(
                "id", review.getId().toString(),
                "submittedAt", review.getSubmittedAt().toString(),
                "moderation", "pending",
                "message", "Thank you. Your review will be published after moderation."
        );
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getPublicReviews(Short minRating, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findPublicReviews(minRating, pageable);
        
        return reviews.map(review -> Map.of(
                "id", review.getId().toString(),
                "reviewerName", review.getReviewerName(),
                "eventType", review.getEventType(),
                "eventDate", review.getEventDate().toString(),
                "overallRating", review.getOverallRating(),
                "comments", review.getComments() != null ? review.getComments() : "",
                "isFeatured", review.getIsFeatured(),
                "submittedAt", review.getSubmittedAt().toString()
        ));
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getFeaturedReviews(Pageable pageable) {
        Page<Review> reviews = reviewRepository.findFeaturedReviews(pageable);
        
        return reviews.map(review -> Map.of(
                "id", review.getId().toString(),
                "reviewerName", review.getReviewerName(),
                "eventType", review.getEventType(),
                "eventDate", review.getEventDate().toString(),
                "overallRating", review.getOverallRating(),
                "comments", review.getComments() != null ? review.getComments() : "",
                "isFeatured", true,
                "submittedAt", review.getSubmittedAt().toString()
        ));
    }
}
