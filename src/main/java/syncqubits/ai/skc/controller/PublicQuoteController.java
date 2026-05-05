package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.quote.QuoteRequestDto;
import syncqubits.ai.skc.dto.quote.QuoteResponse;
import syncqubits.ai.skc.service.QuoteService;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
@Slf4j
public class PublicQuoteController {

    private final QuoteService quoteService;

    @PostMapping
    public ResponseEntity<QuoteResponse> submitQuote(@Valid @RequestBody QuoteRequestDto request) {
        log.info("Received quote request from: {}", request.getEmail());
        QuoteResponse response = quoteService.submitQuote(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
