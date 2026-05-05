package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.email.SendOneRequest;
import syncqubits.ai.skc.service.CampaignService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/emails")
@RequiredArgsConstructor
@Slf4j
public class AdminEmailController {

    private final CampaignService campaignService;

    @PostMapping("/send-one")
    public ResponseEntity<Map<String, Object>> sendOne(@Valid @RequestBody SendOneRequest body) {
        Map<String, Object> result = campaignService.sendOne(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
