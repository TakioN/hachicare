package com.example.demo.domain.prescription.analyzer.upstage;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * JSON의 값을 뺀 구조만 문자열로 만든다.
 *
 * <p>매핑이 어긋났을 때 실제로 뭐가 왔는지 봐야 고칠 수 있는데, 그 내용이 처방전이라
 * 통째로 로그에 남길 수 없다. 필드 이름과 중첩만 남기면 매핑 수정에는 충분하고
 * 약물명이나 용법은 새어나가지 않는다.
 *
 * <p>예: {@code {reviewStatus, documentType, medicines[{name, dosage}], issues[]}}
 */
final class JsonShape {

    private static final int MAX_ARRAY_SAMPLE = 1;
    private static final int MAX_DEPTH = 6;

    private JsonShape() {
    }

    static String of(JsonNode node) {
        return describe(node, 0);
    }

    private static String describe(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (depth >= MAX_DEPTH) {
            return "…";
        }
        if (node.isObject()) {
            List<String> entries = new ArrayList<>();
            node.propertyNames().forEach(name ->
                    entries.add(name + describeChild(node.get(name), depth)));
            return "{" + String.join(", ", entries) + "}";
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return "[]";
            }
            // 배열은 첫 원소 구조만 보면 된다. 전부 찍으면 길이만 늘고 얻는 게 없다.
            return "[" + describe(node.get(0), depth + 1)
                    + (node.size() > MAX_ARRAY_SAMPLE ? ", …×" + (node.size() - 1) : "") + "]";
        }
        // 스칼라는 타입만 남긴다. 값은 남기지 않는다.
        return node.getNodeType().name().toLowerCase();
    }

    /** 스칼라 필드는 이름만, 구조가 있는 필드는 이름 뒤에 구조를 붙인다. */
    private static String describeChild(JsonNode child, int depth) {
        if (child != null && (child.isObject() || child.isArray())) {
            return describe(child, depth + 1);
        }
        return "";
    }
}
