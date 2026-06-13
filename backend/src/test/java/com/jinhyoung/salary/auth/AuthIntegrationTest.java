package com.jinhyoung.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 로그인 흐름 통합 테스트(AUTH-01, AUTH-03). 실 PostgreSQL을 Testcontainers로 띄워 Flyway V1까지
 * 함께 검증한다(CI엔 DB가 없으므로). OAuthClient만 스텁으로 교체해 네트워크 없이 upsert·JWT·보안을 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    com.jinhyoung.salary.user.infra.UserRepository userRepository;

    @Autowired
    JwtProvider jwtProvider;

    @MockitoBean
    OAuthClient oauthClient;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    private MvcResult login(String provider, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/{provider}", provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andReturn();
    }

    private void stub(OAuthProvider provider, String code, OAuthUserInfo info) {
        given(oauthClient.fetchUserInfo(eq(provider), eq(code))).willReturn(info);
    }

    @Test
    void 신규_카카오_로그인은_사용자를_생성하고_유효한_JWT를_발급한다() throws Exception {
        stub(OAuthProvider.KAKAO, "code-1", new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-1", "a@kakao.com", "월급이"));

        MvcResult result = login("kakao", "code-1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("isNewUser").asBoolean()).isTrue();
        String token = body.get("accessToken").asText();
        assertThat(token).isNotBlank();

        assertThat(userRepository.count()).isEqualTo(1);
        var saved =
                userRepository.findByProviderAndProviderId("KAKAO", "kakao-1").orElseThrow();
        assertThat(jwtProvider.parseUserId(token)).isEqualTo(saved.getId());
        assertThat(saved.getNickname()).isEqualTo("월급이");
    }

    @Test
    void 동일_계정_재로그인은_신규가_아니며_중복_생성하지_않는다() throws Exception {
        stub(OAuthProvider.KAKAO, "code-1", new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-1", "a@kakao.com", "월급이"));

        login("kakao", "code-1");
        MvcResult second = login("kakao", "code-1");

        JsonNode body = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(body.get("isNewUser").asBoolean()).isFalse();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void AUTH_03_같은_providerId라도_공급자가_다르면_별개_계정이다() throws Exception {
        stub(OAuthProvider.KAKAO, "code-k", new OAuthUserInfo(OAuthProvider.KAKAO, "dup-1", "k@x.com", "카카오"));
        stub(OAuthProvider.GOOGLE, "code-g", new OAuthUserInfo(OAuthProvider.GOOGLE, "dup-1", "g@x.com", "구글"));

        login("kakao", "code-k");
        login("google", "code-g");

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(userRepository.findByProviderAndProviderId("KAKAO", "dup-1")).isPresent();
        assertThat(userRepository.findByProviderAndProviderId("GOOGLE", "dup-1"))
                .isPresent();
    }

    @Test
    void 동의거부로_닉네임이_없으면_이메일_로컬파트로_폴백한다() throws Exception {
        stub(
                OAuthProvider.KAKAO,
                "code-1",
                new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-2", "noname@kakao.com", null));

        login("kakao", "code-1");

        var saved =
                userRepository.findByProviderAndProviderId("KAKAO", "kakao-2").orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("noname");
    }

    @Test
    void 비활성_공급자_네이버는_400이고_OAuth교환을_시도하지_않는다() throws Exception {
        MvcResult result = login("naver", "code-1");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("PROVIDER_NOT_SUPPORTED");
        verify(oauthClient, never()).fetchUserInfo(eq(OAuthProvider.NAVER), anyString());
    }

    @Test
    void 미지원_공급자_경로는_400이다() throws Exception {
        MvcResult result = login("twitter", "code-1");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("code")
                        .asText())
                .isEqualTo("PROVIDER_NOT_SUPPORTED");
    }

    @Test
    void code가_비면_400_VALIDATION_FAILED다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/{provider}", "kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("code")
                        .asText())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 토큰_없이_보호_경로_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 유효한_JWT면_인증을_통과한다() throws Exception {
        String token = jwtProvider.issue(42L);

        // 인증은 통과(=401 아님) — 핸들러가 없는 경로라 404. 보안 체인이 토큰을 받아들였다는 증거.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
