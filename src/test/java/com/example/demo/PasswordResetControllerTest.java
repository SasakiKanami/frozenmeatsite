package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void resetTokenCanUpdatePasswordAndIsMarkedConsumed() {
        PasswordResetController controller = new PasswordResetController(
                userRepository, tokenRepository, passwordEncoder, null, "http://localhost", "", false);
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(42);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        User user = new User();
        user.setId(42);
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(42)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secure-password")).thenReturn("bcrypt-hash");

        ResponseEntity<?> response = controller.resetPassword(
                resetRequest("raw-reset-token", "new-secure-password"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bcrypt-hash", user.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertNotNull(token.getConsumedAt());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void consumedResetTokenCannotBeReused() {
        PasswordResetController controller = new PasswordResetController(
                userRepository, tokenRepository, passwordEncoder, null, "http://localhost", "", false);
        PasswordResetToken token = new PasswordResetToken();
        token.setConsumedAt(LocalDateTime.now().minusMinutes(1));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        ResponseEntity<?> response = controller.resetPassword(
                resetRequest("raw-reset-token", "new-secure-password"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any(CharSequence.class));
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
    }

    private PasswordResetCompletionRequest resetRequest(String token, String password) {
        PasswordResetCompletionRequest request = new PasswordResetCompletionRequest();
        request.setToken(token);
        request.setPassword(password);
        return request;
    }
}
