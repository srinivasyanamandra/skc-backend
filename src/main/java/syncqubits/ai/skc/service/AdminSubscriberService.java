package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.subscriber.AdminSubscriberSummary;
import syncqubits.ai.skc.entity.Subscriber;
import syncqubits.ai.skc.entity.SystemLog;
import syncqubits.ai.skc.exception.ResourceNotFoundException;
import syncqubits.ai.skc.repository.SubscriberRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SystemLogService systemLogService;

    public PageResponse<AdminSubscriberSummary> list(String q,
                                                     Boolean active,
                                                     Instant since,
                                                     Instant until,
                                                     int page,
                                                     int size,
                                                     String sortField,
                                                     Sort.Direction direction) {
        Sort sort = Sort.by(direction == null ? Sort.Direction.DESC : direction,
                sortField == null || sortField.isBlank() ? "createdAt" : sortField);

        Page<Subscriber> result = subscriberRepository.searchSubscribers(
                q == null ? "" : q.trim(),
                active, since, until, PageRequest.of(page, size, sort));
        return PageResponse.from(result, this::toSummary);
    }

    @Transactional
    public AdminSubscriberSummary unsubscribe(UUID id) {
        Subscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber " + id + " not found"));
        if (Boolean.FALSE.equals(s.getIsActive())) {
            return toSummary(s);
        }
        s.setIsActive(false);
        s.setUnsubscribedAt(Instant.now());
        subscriberRepository.save(s);

        Map<String, Object> details = new HashMap<>();
        details.put("email", s.getEmail());
        details.put("via", "admin");
        systemLogService.logEmail("unsubscribed", "success", s.getId(), "subscriber", details);

        return toSummary(s);
    }

    @Transactional
    public void delete(UUID id) {
        Subscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber " + id + " not found"));
        subscriberRepository.delete(s);

        Map<String, Object> details = new HashMap<>();
        details.put("email", s.getEmail());
        systemLogService.logEmail("deleted", "success", s.getId(), "subscriber", details);
    }

    private AdminSubscriberSummary toSummary(Subscriber s) {
        return AdminSubscriberSummary.builder()
                .id(s.getId())
                .email(s.getEmail())
                .name(s.getName())
                .source(s.getSource())
                .isActive(s.getIsActive())
                .unsubscribedAt(s.getUnsubscribedAt())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
