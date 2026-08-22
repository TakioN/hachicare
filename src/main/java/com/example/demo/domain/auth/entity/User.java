package com.example.demo.domain.auth.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "ux_users_public_id", columnList = "public_id", unique = true),
        @Index(name = "ux_users_email", columnList = "email", unique = true)
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String PUBLIC_ID_PREFIX = "usr_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 외부에 노출하는 식별자. 토큰 subject와 처방전 작업의 ownerKey로 쓴다.
     * 이메일을 쓰면 이메일이 바뀔 때 소유권이 끊기고, 다른 테이블에 개인정보가 번진다.
     */
    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String publicId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt 해시. 평문은 어디에도 남기지 않는다. */
    @Column(nullable = false, length = 60)
    private String password;

    @Column(columnDefinition = "TEXT")
    private String description;

    private User(String email, String encodedPassword) {
        this.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
        this.email = email;
        this.password = encodedPassword;
    }

    public static User of(String email, String encodedPassword) {
        return new User(email, encodedPassword);
    }
}
