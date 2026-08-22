package com.example.demo.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.global.session.AnonymousSessionManager;
import com.example.demo.global.storage.StorageService;
import com.example.demo.support.TestImages;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.rate-limit.session.limit=2",
    "app.rate-limit.session.window=10m",
    "app.rate-limit.ip.limit=3",
    "app.rate-limit.ip.window=10m"
})
class RateLimitInterceptorTest {

    private static final String ENDPOINT = "/api/v1/prescription-extractions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void reset() {
        Set<String> keys = redisTemplate.keys("rate:extraction:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        given(storageService.upload(any(), anyString()))
                .willAnswer(invocation -> "prescriptions/" + invocation.getArgument(1) + ".png");
    }

    private MockMultipartFile validDocument() {
        return new MockMultipartFile("document", "p.png", "image/png", TestImages.png());
    }

    private MvcResult upload(String sessionKey) throws Exception {
        var request = multipart(ENDPOINT).file(validDocument());
        if (sessionKey != null) {
            request = request.cookie(new Cookie(AnonymousSessionManager.COOKIE_NAME, sessionKey));
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void 세션_한도까지는_통과하고_넘으면_429다() throws Exception {
        assertThat(upload("session-a").getResponse().getStatus()).isEqualTo(202);
        assertThat(upload("session-a").getResponse().getStatus()).isEqualTo(202);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(validDocument())
                        .cookie(new Cookie(AnonymousSessionManager.COOKIE_NAME, "session-a")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 한도를_넘으면_Retry_After를_알려준다() throws Exception {
        upload("session-b");
        upload("session-b");
        MvcResult blocked = upload("session-b");

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        String retryAfter = blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 600L);
    }

    @Test
    void 세션이_다르면_한도를_따로_센다() throws Exception {
        upload("session-c");
        upload("session-c");
        assertThat(upload("session-c").getResponse().getStatus()).isEqualTo(429);

        // 다른 세션은 자기 몫이 남아 있다 (IP 한도 3에 걸리기 전까지)
        assertThat(upload("session-d").getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void 쿠키를_버려도_IP_한도에는_걸린다() throws Exception {
        // 매번 새 세션이라 세션 한도(2)는 안 걸리지만 IP 한도(3)는 누적된다
        assertThat(upload("fresh-1").getResponse().getStatus()).isEqualTo(202);
        assertThat(upload("fresh-2").getResponse().getStatus()).isEqualTo(202);
        assertThat(upload("fresh-3").getResponse().getStatus()).isEqualTo(202);

        MvcResult blocked = upload("fresh-4");
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void 상태_조회는_한도_대상이_아니다() throws Exception {
        upload("session-e");
        upload("session-e");
        assertThat(upload("session-e").getResponse().getStatus()).isEqualTo(429);

        // 폴링은 정상 동작이므로 막지 않는다. 남의 작업이라 403이지만 429는 아니다.
        mockMvc.perform(get(ENDPOINT + "/ext_something")
                        .cookie(new Cookie(AnonymousSessionManager.COOKIE_NAME, "session-e")))
                .andExpect(status().isNotFound());
    }
}
