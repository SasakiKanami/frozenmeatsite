package com.example.demo;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/profile")
public class AccountController {

    private final UserRepository userRepository;

    public AccountController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getProfile(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toProfile(user));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @RequestBody ProfileUpdateRequest request) {
        if (request == null || isBlank(request.getFullName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
        }
        if (request.getFullName().trim().length() > 100
                || length(request.getPhone()) > 30
                || length(request.getAddressLine()) > 500
                || length(request.getCity()) > 100
                || length(request.getLandmark()) > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "One or more profile fields exceed the allowed length"));
        }

        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) return ResponseEntity.notFound().build();
        user.setFullName(request.getFullName().trim());
        user.setPhone(blankToNull(request.getPhone()));
        user.setAddressLine(blankToNull(request.getAddressLine()));
        user.setCity(blankToNull(request.getCity()));
        user.setLandmark(blankToNull(request.getLandmark()));
        return ResponseEntity.ok(toProfile(userRepository.save(user)));
    }

    private Map<String, Object> toProfile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("addressLine", user.getAddressLine());
        profile.put("city", user.getCity());
        profile.put("landmark", user.getLandmark());
        return profile;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private int length(String value) { return value == null ? 0 : value.length(); }
    private String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
}

class ProfileUpdateRequest {
    private String fullName;
    private String phone;
    private String addressLine;
    private String city;
    private String landmark;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
}
