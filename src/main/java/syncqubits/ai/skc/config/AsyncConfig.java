package syncqubits.ai.skc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration for {@code @Async} email/campaign workloads.
 *
 * Design notes:
 *   - Bean is registered under both {@code "taskExecutor"} (Spring's default
 *     {@code @Async} lookup) and {@code "emailTaskExecutor"} (legacy callers).
 *   - Marked {@link Primary} so it wins over the {@code taskScheduler} bean
 *     auto-registered by {@code @EnableScheduling}.
 *   - Capacity sized for typical campaign batches; with queue=1000 and the
 *     {@link ThreadPoolExecutor.CallerRunsPolicy} fallback, even sudden
 *     bursts cannot silently lose work.
 *   - Rejection policy is {@code CallerRunsPolicy}, not the JDK default
 *     {@code AbortPolicy}. When the queue is full, the thread that
 *     submitted the task runs it inline. This applies natural back-
 *     pressure to the caller (the dispatch loop slows down) instead of
 *     silently dropping emails — critical for at-least-once semantics.
 */
@Configuration
@Slf4j
public class AsyncConfig {

    @Bean(name = {"taskExecutor", "emailTaskExecutor"})
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        /* Larger queue + CallerRunsPolicy means a 1000-recipient campaign
           can fully buffer instead of dropping the tail half. */
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        /* CallerRunsPolicy: when queue is full, the calling thread runs
           the task synchronously. Trades throughput for reliability — far
           better than silently rejecting the email. */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("AsyncConfig initialised: core=2, max=8, queue=1000, policy=CallerRuns");
        return executor;
    }
}
