package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/signup")
    public ResponseEntity signup(@RequestBody User newUser) {
        if (userRepository.findByUsername(newUser.getUsername()) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        // Force new signups from the storefront to be standard customers
        newUser.setRole("customer");
        userRepository.save(newUser);

        return ResponseEntity.ok(Map.of("message", "Account created successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        User user = userRepository.findByUsername(username);

        // Basic verification
        if (user != null && user.getPasswordHash().equals(password)) {
            // Returns the role so your leader's frontend can trigger the correct redirect
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "role", user.getRole(),
                    "userId", user.getId()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
}

