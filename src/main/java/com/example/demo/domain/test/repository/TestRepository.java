package com.example.demo.domain.test.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.test.entity.User;

public interface TestRepository extends JpaRepository<User, Long> {
    boolean existsByUserId(String userId);

    Optional<User> findByEmail(String email);
} 
