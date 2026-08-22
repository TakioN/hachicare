package com.example.demo.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    String accessKey,
    String secretKey,
    String region,
    String endpoint,
    String bucketName
) {
}
