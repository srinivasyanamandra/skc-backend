package syncqubits.ai.skc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * The default executor used by every {@code @Async} method.
     *
     * Bean is registered under both names so:
     *   - Spring's {@code @Async} default lookup ({@code "taskExecutor"}) resolves it,
     *   - existing call sites that referenced {@code "emailTaskExecutor"} keep working.
     *
     * Marked {@link Primary} so it wins over the {@code taskScheduler} bean
     * auto-registered by {@code @EnableScheduling}.
     */
    @Bean(name = {"taskExecutor", "emailTaskExecutor"})
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
