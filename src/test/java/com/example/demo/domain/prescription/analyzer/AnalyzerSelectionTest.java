package com.example.demo.domain.prescription.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.example.demo.domain.prescription.analyzer.upstage.UpstagePrescriptionAnalyzer;

/**
 * app.analyzer 스위치가 실제로 동작하는지 확인한다.
 *
 * <p>조건부 빈은 설정을 바꾸는 순간에만 문제가 드러나므로, 양쪽 모두 컨텍스트가 뜨는지
 * 미리 확인해 둔다. upstage 쪽은 api-key나 agent-id가 없어도 기동은 되어야 한다
 * (실제 호출 시점에야 필요하다).
 */
class AnalyzerSelectionTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "app.analyzer=stub")
    class 스텁_모드 {

        @Autowired
        private PrescriptionAnalyzer analyzer;

        @Test
        void 스텁_분석기가_선택된다() {
            assertThat(analyzer).isInstanceOf(StubPrescriptionAnalyzer.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
        "app.analyzer=upstage",
        "upstage.agent-id=agt_test",
        "upstage.api-key=up-test"
    })
    class 업스테이지_모드 {

        @Autowired
        private PrescriptionAnalyzer analyzer;

        @Test
        void 업스테이지_분석기가_선택된다() {
            assertThat(analyzer).isInstanceOf(UpstagePrescriptionAnalyzer.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "app.analyzer=upstage")
    class 키가_없는_업스테이지_모드 {

        @Autowired
        private PrescriptionAnalyzer analyzer;

        @Test
        void 키가_없어도_기동은_된다() {
            // 키는 호출할 때 필요하지 배선에는 필요 없다. 기동이 막히면 배포가 곤란해진다.
            assertThat(analyzer).isInstanceOf(UpstagePrescriptionAnalyzer.class);
        }
    }
}
