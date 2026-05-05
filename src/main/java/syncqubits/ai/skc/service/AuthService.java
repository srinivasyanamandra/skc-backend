package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import syncqubits.ai.skc.config.AppProperties;
import syncqubits.ai.skc.dto.auth.LoginRequest;
import syncqubits.ai.skc.dto.auth.LoginResponse;
import syncqubits.ai.skc.exception.UnauthorizedException;
import syncqubits.ai.skc.security.JwtService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SystemLogService systemLogService;

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Validate credentials against hardcoded admin list
        if (!isValidAdmin(request.getEmail(), request.getPassword())) {
            systemLogService.logAuth("login", "failed", request.getEmail(), ipAddress, userAgent);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect.");
        }

        // Generate JWT token
        String token = jwtService.generateToken(request.getEmail());
        var expiresAt = jwtService.getExpirationFromToken(token);

        systemLogService.logAuth("login", "success", request.getEmail(), ipAddress, userAgent);

        return LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .user(LoginResponse.UserInfo.builder()
                        .email(request.getEmail())
                        .role("ADMIN")
                        .build())
                .build();
    }

    private boolean isValidAdmin(String email, String password) {
        var adminEmails = appProperties.getAdmin().getEmails();
        var adminPasswords = appProperties.getAdmin().getPasswords();

        if (adminEmails == null || adminEmails.isEmpty()
                || adminPasswords == null || adminPasswords.isEmpty()) {
            log.error("Admin credentials not configured");
            return false;
        }

        // Validate that emails and passwords lists have the same size
        if (adminEmails.size() != adminPasswords.size()) {
            log.error("Configuration error: admin.emails has {} entries but admin.passwords has {} entries. " +
                     "These lists must have the same size. Check your application.yml and environment variables.",
                     adminEmails.size(), adminPasswords.size());
            return false;
        }

        // Find the matching email index
        int idx = -1;
        for (int i = 0; i < adminEmails.size(); i++) {
            if (adminEmails.get(i).equalsIgnoreCase(email)) {
                idx = i;
                break;
            }
        }
        
        if (idx < 0) {
            log.debug("Email '{}' not found in admin.emails list", email);
            return false;
        }

        String storedPassword = adminPasswords.get(idx);
        if (storedPassword == null || storedPassword.isBlank()) {
            log.error("Password at index {} for email '{}' is null or blank", idx, email);
            return false;
        }

        log.debug("Validating password for admin email '{}' at index {}. Stored password starts with: {}", 
                  email, idx, storedPassword.substring(0, Math.min(5, storedPassword.length())) + "...");

        // BCrypt-hashed or plain text
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(password, storedPassword);
        }
        return password.equals(storedPassword);
    }
}
