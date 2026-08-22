package com.example.demo.global.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 쿠키 Secure 플래그는 설정 누락이 곧 보안 결함이 되므로, 커밋되는 기본값이
 * 안전한 쪽인지 확인한다.
 *
 * <p>프로파일별 override는 gitignore된 application-{profile}.yml에 있어 환경마다
 * 달라지므로 여기서 단언하지 않는다. 여기서 지키는 것은 "아무 데서도 내리지 않으면
 * 켜진 상태로 떨어진다"는 것뿐이다.
 *
 * <p>빈을 띄우지 않고 설정만 로드하므로 DB가 없어도 된다.
 */
class SessionCookieSecurityConfigTest {

    private static final String PROPERTY = "app.session.cookie-secure";

    private String resolveWithoutProfileOverride() {
        StringBuilder resolved = new StringBuilder();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                // 프로파일 파일을 타지 않도록 정의되지 않은 프로파일로 로드한다
                .withPropertyValues("spring.profiles.active=none")
                .run(context -> resolved.append(context.getEnvironment().getProperty(PROPERTY)));
        return resolved.toString();
    }

    @Test
    void 프로파일에서_내리지_않으면_Secure가_켜진다() {
        assertThat(resolveWithoutProfileOverride()).isEqualTo("true");
    }

    @Test
    void 설정이_통째로_빠져도_코드_폴백이_안전한_쪽이다() throws Exception {
        var annotation = AnonymousSessionManager.class
                .getDeclaredConstructor(boolean.class)
                .getParameters()[0]
                .getAnnotation(org.springframework.beans.factory.annotation.Value.class);

        assertThat(annotation.value()).isEqualTo("${app.session.cookie-secure:true}");
    }
}
