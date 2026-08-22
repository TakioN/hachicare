package com.example.demo.domain.test.service;

import org.springframework.stereotype.Service;

import com.example.demo.domain.auth.entity.User;
import com.example.demo.domain.auth.repository.UserRepository;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestService {

    private final UserRepository userRepository;

    public String getDesc(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getDescription();
    }
}
