package com.example.demo.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.auth.repository.UserRepository;
import com.example.demo.domain.auth.service.RefreshTokenStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    private MvcResult postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private JsonNode tokensFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode signUpAndSignIn() throws Exception {
        assertThat(postJson("/api/signup", credentials(EMAIL, PASSWORD)).getResponse().getStatus())
                .isEqualTo(201);
        MvcResult signedIn = postJson("/api/signin", credentials(EMAIL, PASSWORD));
        assertThat(signedIn.getResponse().getStatus()).isEqualTo(200);
        return tokensFrom(signedIn);
    }

    @Test
    void 가입하면_비밀번호는_해시로_저장된다() throws Exception {
        postJson("/api/signup", credentials(EMAIL, PASSWORD));

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(user.getPublicId()).startsWith("usr_");
    }

    @Test
    void 같은_이메일로_두_번_가입하면_409() throws Exception {
        postJson("/api/signup", credentials(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(EMAIL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_already_exists"));
    }

    @Test
    void 로그인하면_두_토큰을_받는다() throws Exception {
        JsonNode tokens = signUpAndSignIn();

        assertThat(tokens.get("accessToken").stringValue()).isNotBlank();
        assertThat(tokens.get("refreshToken").stringValue()).isNotBlank();
    }

    @Test
    void 없는_계정과_틀린_비밀번호는_같은_응답을_준다() throws Exception {
        postJson("/api/signup", credentials(EMAIL, PASSWORD));

        for (String body : new String[] {
            credentials(EMAIL, "wrong-password"),
            credentials("nobody@example.com", PASSWORD)
        }) {
            mockMvc.perform(post("/api/signin").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("invalid_credentials"));
        }
    }

    @Test
    void 받은_토큰으로_보호된_경로에_들어갈_수_있다() throws Exception {
        JsonNode tokens = signUpAndSignIn();

        mockMvc.perform(get("/api/v1/prescription-extractions/ext_nope")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokens.get("accessToken").stringValue()))
                // 인증은 통과했으니 401이 아니라 404가 나와야 한다
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("extraction_not_found"));
    }

    @Test
    void 재발급하면_새_토큰이_나오고_쓴_refresh는_폐기된다() throws Exception {
        JsonNode tokens = signUpAndSignIn();
        String oldRefresh = tokens.get("refreshToken").stringValue();

        MvcResult refreshed = postJson("/api/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}");
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);

        String newRefresh = tokensFrom(refreshed).get("refreshToken").stringValue();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(refreshTokenStore.findOwner(oldRefresh)).isEmpty();
        assertThat(refreshTokenStore.findOwner(newRefresh)).isPresent();
    }

    @Test
    void 이미_쓴_refresh로_다시_재발급하면_401() throws Exception {
        JsonNode tokens = signUpAndSignIn();
        String oldRefresh = tokens.get("refreshToken").stringValue();
        postJson("/api/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}");

        mockMvc.perform(post("/api/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_token"));
    }

    @Test
    void 로그아웃하면_그_refresh로_재발급할_수_없다() throws Exception {
        JsonNode tokens = signUpAndSignIn();
        String refresh = tokens.get("refreshToken").stringValue();

        mockMvc.perform(post("/api/signout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 없는_토큰으로_로그아웃해도_204() throws Exception {
        // 멱등해야 하고, 존재 여부를 알려주지 않아야 한다
        mockMvc.perform(post("/api/signout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"never-issued\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 같은_순간에_발급해도_토큰은_서로_다르다() throws Exception {
        // 같은 초에 발급되면 subject와 만료가 같아 글자까지 같아질 수 있다.
        // 그러면 refresh 회전이 헛돌아 일회용 성질이 깨진다.
        JsonNode first = signUpAndSignIn();
        MvcResult second = postJson("/api/signin", credentials(EMAIL, PASSWORD));

        assertThat(tokensFrom(second).get("refreshToken").stringValue())
                .isNotEqualTo(first.get("refreshToken").stringValue());
    }

    @Test
    void 공개_경로는_인증_없이_열려_있다() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
