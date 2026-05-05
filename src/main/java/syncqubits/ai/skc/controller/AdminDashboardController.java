package syncqubits.ai.skc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.service.DashboardService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard() {
        log.info("Fetching admin dashboard");
        Map<String, Object> dashboard = dashboardService.getDashboardData();
        return ResponseEntity.ok(dashboard);
    }
}
