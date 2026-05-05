package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.review.ReviewSubmitRequest;
import syncqubits.ai.skc.service.ReviewService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/reviews")
@RequiredArgsConstructor
@Slf4j
public class PublicReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getReviewInvitation(@PathVariable String token) {
        log.info("Fetching review invitation for token: {}", token);
        Map<String, Object> response = reviewService.getReviewInvitation(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> submitReview(
            @PathVariable String token,
            @Valid @RequestBody ReviewSubmitRequest request
    ) {
        log.info("Submitting review for token: {}", token);
        Map<String, Object> response = reviewService.submitReview(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicReviews(
            @RequestParam(required = false) Short minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int limit
    ) {
        log.info("Fetching public reviews - page: {}, limit: {}, minRating: {}", page, limit, minRating);
        
        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Map<String, Object>> reviews = reviewService.getPublicReviews(minRating, pageable);
        
        Map<String, Object> response = Map.of(
                "page", reviews.getNumber(),
                "size", reviews.getSize(),
                "total", reviews.getTotalElements(),
                "items", reviews.getContent()
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/featured")
    public ResponseEntity<Map<String, Object>> getFeaturedReviews(
            @RequestParam(defaultValue = "6") int limit
    ) {
        log.info("Fetching featured reviews - limit: {}", limit);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Map<String, Object>> reviews = reviewService.getFeaturedReviews(pageable);
        
        Map<String, Object> response = Map.of(
                "page", 0,
                "size", reviews.getSize(),
                "total", reviews.getTotalElements(),
                "items", reviews.getContent()
        );
        
        return ResponseEntity.ok(response);
    }
}
