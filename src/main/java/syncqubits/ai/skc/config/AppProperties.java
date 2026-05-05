package syncqubits.ai.skc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "")
@Data
public class AppProperties {

    private Admin admin = new Admin();
    private Jwt jwt = new Jwt();
    private Review review = new Review();
    private Campaign campaign = new Campaign();
    private Cors cors = new Cors();

    @Data
    public static class Admin {
        private List<String> emails;
        private List<String> passwords;
    }

    @Data
    public static class Jwt {
        private String secret;
        private Long expiration;
    }

    @Data
    public static class Review {
        private Link link = new Link();

        @Data
        public static class Link {
            private Integer defaultExpiresDays;
            private String baseUrl;
        }
    }

    @Data
    public static class Campaign {
        private Throttle throttle = new Throttle();
        private Integer defaultBatchSize;

        @Data
        public static class Throttle {
            private Integer perMinute;
        }
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }
}
