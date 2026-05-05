package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.subscriber.SubscribeRequest;
import syncqubits.ai.skc.dto.subscriber.SubscribeResponse;
import syncqubits.ai.skc.service.SubscriberService;

@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
@Slf4j
public class PublicSubscriberController {

    private final SubscriberService subscriberService;

    @PostMapping
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        log.info("Received subscription request for: {}", request.getEmail());
        SubscribeResponse response = subscriberService.subscribe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
