package syncqubits.ai.skc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Wires the Document Studio asset directory into Spring MVC.
 *
 * <p>Files uploaded via {@code POST /api/admin/assets} land on disk under
 * {@code studio.assets.dir}; this configurer publishes that directory at
 * {@code /uploads/**} so {@code <img src="/uploads/<filename>">} tags work
 * in the admin (and, later, inside the server-rendered letterhead PDF).
 *
 * <p>The same directory is created at startup if missing — so the server
 * boots cleanly on a fresh checkout without the operator having to mkdir.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StudioAssetsConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @PostConstruct
    void ensureAssetsDir() {
        Path dir = Paths.get(appProperties.getStudio().getAssets().getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            log.info("Studio assets directory ready at {}", dir);
        } catch (Exception e) {
            log.warn("Could not create studio assets directory {}: {}", dir, e.getMessage());
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base = appProperties.getStudio().getAssets().getPublicBase();
        String pattern = (base.endsWith("/") ? base : base + "/") + "**";

        String location = Paths.get(appProperties.getStudio().getAssets().getDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        registry.addResourceHandler(pattern)
                .addResourceLocations(location)
                /* No far-future caching — admins editing brand assets need
                   immediate visual feedback when they re-upload the same
                   role. A small cache keeps repeat renders snappy. */
                .setCachePeriod(60);

        log.info("Studio assets served at {} from {}", pattern, location);
    }
}
