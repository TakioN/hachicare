package com.example.demo.global.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnonymousSessionManagerTest {

    private String issuedCookie(boolean secure, String sameSite) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AnonymousSessionManager(secure, sameSite)
                .resolveOrIssue(new MockHttpServletRequest(), response);
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void 설정한_SameSite_값이_쿠키에_실린다() {
        assertThat(issuedCookie(false, "Lax")).contains("SameSite=Lax");
        assertThat(issuedCookie(true, "None")).contains("SameSite=None");
    }

    @Test
    void 발급한_쿠키는_HttpOnly다() {
        assertThat(issuedCookie(false, "Lax")).contains("HttpOnly");
    }

    @Test
    void Secure_없이_None을_쓰면_기동을_막는다() {
        // 브라우저가 조용히 버리는 조합이라 런타임에 원인 모를 403으로 나타난다
        assertThatThrownBy(() -> new AnonymousSessionManager(false, "None"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookie-secure");

        assertThatThrownBy(() -> new AnonymousSessionManager(false, "none"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void Secure와_함께면_None을_허용한다() {
        assertThatCode(() -> new AnonymousSessionManager(true, "None"))
                .doesNotThrowAnyException();
    }
}
