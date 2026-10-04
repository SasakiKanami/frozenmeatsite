package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

    User findByUsername(String username);

    User findFirstByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
}