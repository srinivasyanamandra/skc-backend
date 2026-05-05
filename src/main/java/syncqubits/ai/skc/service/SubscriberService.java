package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.subscriber.SubscribeRequest;
import syncqubits.ai.skc.dto.subscriber.SubscribeResponse;
import syncqubits.ai.skc.entity.Subscriber;
import syncqubits.ai.skc.exception.ConflictException;
import syncqubits.ai.skc.repository.SubscriberRepository;
import syncqubits.ai.skc.util.NameUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;

    @Transactional
    public SubscribeResponse subscribe(SubscribeRequest request) {
        log.info("Processing subscription request for email: {}", request.getEmail());

        // Derive a clean name from email if not provided
        String derivedName = NameUtils.resolveDisplayName(request.getName(), request.getEmail());
        
        // Check if already subscribed
        var existing = subscriberRepository.findByEmail(request.getEmail());
        if (existing.isPresent() && existing.get().getIsActive()) {
            throw new ConflictException("Email is already subscribed");
        }

        // If previously unsubscribed, reactivate
        if (existing.isPresent()) {
            Subscriber subscriber = existing.get();
            subscriber.setIsActive(true);
            subscriber.setUnsubscribedAt(null);
            // Update name with derived value (prefer provided name, derive from email if blank)
            subscriber.setName(derivedName);
            subscriberRepository.save(subscriber);
            log.info("Reactivated subscription for: {} with name: {}", request.getEmail(), derivedName);
        } else {
            // Create new subscriber with derived name
            Subscriber subscriber = Subscriber.builder()
                    .email(request.getEmail())
                    .name(derivedName)
                    .source("website")
                    .isActive(true)
                    .build();
            subscriberRepository.save(subscriber);
            log.info("New subscription created for: {} with name: {}", request.getEmail(), derivedName);
        }

        return SubscribeResponse.builder()
                .subscribed(true)
                .email(request.getEmail())
                .build();
    }
}
