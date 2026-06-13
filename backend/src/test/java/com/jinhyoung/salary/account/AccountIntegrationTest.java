package com.jinhyoung.salary.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통장 CRUD + 소유권 검증 통합 테스트(SET-04). 실 PostgreSQL을 Testcontainers로 띄워
 * Flyway V1(accounts·users FK 포함)까지 함께 검증한다. 인증은 실제 JWT를 발급해 Bearer로 건다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        aliceId = newUser("alice");
        bobId = newUser("bob");
        aliceToken = jwtProvider.issue(aliceId);
    }

    private long newUser(String key) {
        User user = userRepository.save(User.createFromOAuth("KAKAO", key, key + "@x.com", key));
        return user.getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long createAccount(String token, String name) throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/accounts"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void 통장을_생성하면_목록에_나오고_sortOrder가_끝자리로_부여된다() throws Exception {
        mockMvc.perform(authed(post("/api/v1/accounts"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"케이뱅크\",\"purpose\":\"생활비\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("케이뱅크"))
                .andExpect(jsonPath("$.purpose").value("생활비"))
                .andExpect(jsonPath("$.sortOrder").value(0));

        createAccount(aliceToken, "국민");

        mockMvc.perform(authed(get("/api/v1/accounts"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("케이뱅크"))
                .andExpect(jsonPath("$[1].name").value("국민"))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
    }

    @Test
    void 다른_사용자의_통장은_목록에_보이지_않는다() throws Exception {
        createAccount(jwtProvider.issue(bobId), "밥의통장");

        mockMvc.perform(authed(get("/api/v1/accounts"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 다른_사용자의_통장은_수정할_수_없다_소유권_위반_차단() throws Exception {
        long bobAccount = createAccount(jwtProvider.issue(bobId), "밥의통장");

        mockMvc.perform(authed(patch("/api/v1/accounts/{id}", bobAccount), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"탈취\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 밥의 통장은 그대로 — 앨리스의 요청이 아무것도 바꾸지 못했다.
        assertThat(accountRepository.findById(bobAccount).orElseThrow().getName())
                .isEqualTo("밥의통장");
    }

    @Test
    void 다른_사용자의_통장은_삭제할_수_없다_소유권_위반_차단() throws Exception {
        long bobAccount = createAccount(jwtProvider.issue(bobId), "밥의통장");

        mockMvc.perform(authed(delete("/api/v1/accounts/{id}", bobAccount), aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(accountRepository.findById(bobAccount).orElseThrow().isActive())
                .isTrue();
    }

    @Test
    void 통장을_수정하면_별칭_용도_정렬이_갱신된다() throws Exception {
        long id = createAccount(aliceToken, "옛이름");

        mockMvc.perform(authed(patch("/api/v1/accounts/{id}", id), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"purpose\":\"비상금\",\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.purpose").value("비상금"))
                .andExpect(jsonPath("$.sortOrder").value(5));
    }

    @Test
    void 통장을_삭제하면_목록에서_사라지지만_행은_남는다_soft_delete() throws Exception {
        long id = createAccount(aliceToken, "지울통장");

        mockMvc.perform(authed(delete("/api/v1/accounts/{id}", id), aliceToken)).andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/accounts"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 물리 삭제가 아니다 — 과거 스냅샷·plan_lines 참조 유지를 위해 행은 비활성으로 남는다(규칙 5).
        Account deleted = accountRepository.findById(id).orElseThrow();
        assertThat(deleted.isActive()).isFalse();
    }

    @Test
    void 삭제된_통장은_다시_삭제하거나_수정할_수_없다() throws Exception {
        long id = createAccount(aliceToken, "지울통장");
        mockMvc.perform(authed(delete("/api/v1/accounts/{id}", id), aliceToken)).andExpect(status().isNoContent());

        mockMvc.perform(authed(patch("/api/v1/accounts/{id}", id), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"되살리기\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 활성_통장이_20개면_추가_생성은_409_ACCOUNT_LIMIT_EXCEEDED다() throws Exception {
        for (int i = 0; i < 20; i++) {
            createAccount(aliceToken, "통장" + i);
        }

        mockMvc.perform(authed(post("/api/v1/accounts"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"스물한번째\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LIMIT_EXCEEDED"));
    }

    @Test
    void 삭제로_자리가_나면_상한_안에서_다시_생성할_수_있다() throws Exception {
        long first = createAccount(aliceToken, "통장0");
        for (int i = 1; i < 20; i++) {
            createAccount(aliceToken, "통장" + i);
        }
        mockMvc.perform(authed(delete("/api/v1/accounts/{id}", first), aliceToken))
                .andExpect(status().isNoContent());

        // 활성 19개로 줄었으니 한 개 더 생성 가능.
        mockMvc.perform(authed(post("/api/v1/accounts"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"빈자리\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 이름이_비면_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(authed(post("/api/v1/accounts"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 이름이_50자를_넘으면_400_VALIDATION_FAILED다() throws Exception {
        String tooLong = "가".repeat(51);
        mockMvc.perform(authed(post("/api/v1/accounts"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 토큰_없이_통장_목록_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
