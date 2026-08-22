package com.example.demo.domain.prescription.analyzer.upstage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstageFile(
    String id
) {
}
