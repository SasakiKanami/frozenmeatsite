package com.example.demo;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null || user.getRole() == null || user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Invalid username or password");
        }

        UserBuilder builder = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().toUpperCase(Locale.ROOT));
        return builder.build();
    }

    @Override
    public UserDetails updatePassword(UserDetails userDetails, String newPassword) {
        User user = userRepository.findByUsername(userDetails.getUsername());
        if (user == null) {
            throw new UsernameNotFoundException("Account no longer exists");
        }
        user.setPasswordHash(newPassword);
        userRepository.save(user);
        return loadUserByUsername(user.getUsername());
    }
}
