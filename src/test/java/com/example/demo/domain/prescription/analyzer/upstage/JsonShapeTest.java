package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class JsonShapeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String shapeOf(String raw) {
        return JsonShape.of(JSON.readTree(raw));
    }

    @Test
    void 필드_이름과_중첩만_남긴다() {
        String shape = shapeOf("""
                {"reviewStatus":"ready","medicines":[{"name":"약","dosage":1}],"issues":[]}""");

        assertThat(shape).isEqualTo("{reviewStatus, medicines[{name, dosage}], issues[]}");
    }

    @Test
    void 처방전_내용은_흘리지_않는다() {
        // 이 로그는 매핑 실패 시 남는다. 약물명이나 용법이 새면 안 된다.
        String shape = shapeOf("""
                {"medications":[{
                   "drugName":"아모잘탄정 5/50mg",
                   "timing":"아침 식후 30분",
                   "patientName":"홍길동",
                   "rrn":"900101-1234567"
                 }]}""");

        assertThat(shape)
                .doesNotContain("아모잘탄정", "아침 식후", "홍길동", "900101")
                .contains("drugName", "timing", "patientName", "rrn");
    }

    @Test
    void 배열은_첫_원소_구조만_보여주고_나머지는_개수로_줄인다() {
        assertThat(shapeOf("""
                {"medications":[{"id":"a"},{"id":"b"},{"id":"c"}]}"""))
                .isEqualTo("{medications[{id}, …×2]}");
    }

    @Test
    void 스칼라만_있는_최상위도_처리한다() {
        assertThat(shapeOf("\"그냥 문자열\"")).isEqualTo("string");
        assertThat(shapeOf("null")).isEqualTo("null");
        assertThat(shapeOf("[]")).isEqualTo("[]");
    }

    @Test
    void 너무_깊으면_잘라낸다() {
        String deep = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":{\"f\":{\"g\":{\"h\":1}}}}}}}}";

        assertThat(shapeOf(deep)).contains("…").hasSizeLessThan(60);
    }
}
