package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);
    private static final String GENERIC_RESPONSE =
            "If an account exists for that email, password reset instructions will be sent.";
    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String publicBaseUrl;
    private final String mailFrom;
    private final boolean mailEnabled;

    public PasswordResetController(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.public-base-url:http://localhost:8081}") String publicBaseUrl,
            @Value("${app.mail.from:}") String mailFrom,
            @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.publicBaseUrl = publicBaseUrl;
        this.mailFrom = mailFrom;
        this.mailEnabled = mailEnabled;
    }

    @PostMapping("/password-reset-requests")
    @Transactional
    public ResponseEntity<Map<String, String>> requestReset(@RequestBody PasswordResetRequest request) {
        String email = request == null || request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return genericResponse();
        }

        User user = userRepository.findFirstByEmailIgnoreCase(email);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (user == null) return genericResponse();
        if (!mailEnabled || mailSender == null || mailFrom.isBlank()) {
            logger.warn("Password reset requested for user ID {} but SMTP is not configured", user.getId());
            return genericResponse();
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tokenRepository.deleteByUserIdAndConsumedAtIsNull(user.getId());

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        tokenRepository.save(token);

        String resetUrl = publicBaseUrl.replaceAll("/+$", "")
                + "/html/login/login.html?resetToken="
                + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Reset your J&R Frozen Goods password");
        message.setText("Use this one-time link within 30 minutes to reset your password:\n\n"
                + resetUrl + "\n\nIf you did not request a reset, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            tokenRepository.delete(token);
            logger.error("Unable to send a password reset email for user ID {}", user.getId(), exception);
        }
        return genericResponse();
    }

    @PostMapping("/password-resets")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetCompletionRequest request) {
        if (request == null || request.getToken() == null || request.getPassword() == null
                || request.getPassword().length() < 8 || request.getPassword().length() > 72) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid reset token and password of 8 to 72 characters are required"));
        }
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(hashToken(request.getToken()))
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (token == null || token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return ResponseEntity.badRequest().body(Map.of("error", "This password reset link is invalid or expired"));
        }
        User user = userRepository.findById(token.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "This password reset link is invalid or expired"));
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        token.setConsumedAt(now);
        tokenRepository.save(token);
        return ResponseEntity.ok(Map.of("message", "Password updated. You can now sign in."));
    }

    private ResponseEntity<Map<String, String>> genericResponse() {
        return ResponseEntity.ok(Map.of("message", GENERIC_RESPONSE));
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

class PasswordResetRequest {
    private String email;
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

class PasswordResetCompletionRequest {
    private String token;
    private String password;
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
