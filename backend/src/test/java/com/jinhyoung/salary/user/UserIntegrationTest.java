package com.jinhyoung.salary.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
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
 * 프로필·기본 정보 설정 통합 테스트(SET-01). 실 PostgreSQL을 Testcontainers로 띄워 Flyway V1(users·accounts)까지
 * 검증한다. 인증은 실제 JWT를 발급해 Bearer로 건다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserIntegrationTest {

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

    private MockHttpServletRequestBuilder patchMe(String token, String body) {
        return authed(patch("/api/v1/me"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void 신규_사용자의_GET_me는_온보딩_전_플레이스홀더를_반환한다() throws Exception {
        mockMvc.perform(authed(get("/api/v1/me"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceId))
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.baseIncome").value(0))
                .andExpect(jsonPath("$.payday").value(1))
                .andExpect(jsonPath("$.paydayAdjustment").value("NONE"))
                .andExpect(jsonPath("$.includeInvestmentInSavingsRate").value(true))
                .andExpect(jsonPath("$.locale").value("ko"))
                .andExpect(jsonPath("$.livingAccountId").value(nullValue()));
    }

    @Test
    void 기본_정보를_등록하면_값이_반영되고_재조회에서_일치한다() throws Exception {
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"PREV_BUSINESS_DAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseIncome").value(2500000))
                .andExpect(jsonPath("$.payday").value(25))
                .andExpect(jsonPath("$.paydayAdjustment").value("PREV_BUSINESS_DAY"));

        mockMvc.perform(authed(get("/api/v1/me"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseIncome").value(2500000))
                .andExpect(jsonPath("$.payday").value(25))
                .andExpect(jsonPath("$.paydayAdjustment").value("PREV_BUSINESS_DAY"));

        User saved = userRepository.findById(aliceId).orElseThrow();
        assertThat(saved.getBaseIncome()).isEqualTo(2500000L);
        assertThat(saved.getPayday()).isEqualTo((short) 25);
        assertThat(saved.getPaydayAdjustment()).isEqualTo(PaydayAdjustment.PREV_BUSINESS_DAY);
    }

    @Test
    void 본인의_활성_통장을_생활비_통장으로_지정할_수_있다() throws Exception {
        long account = createAccount(aliceToken, "생활비통장");

        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\",\"livingAccountId\":"
                                + account + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.livingAccountId").value(account));

        assertThat(userRepository.findById(aliceId).orElseThrow().getLivingAccountId())
                .isEqualTo(account);
    }

    @Test
    void 타인의_통장은_생활비_통장으로_지정할_수_없다_소유권_위반_차단() throws Exception {
        long bobAccount = createAccount(jwtProvider.issue(bobId), "밥의통장");

        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\",\"livingAccountId\":"
                                + bobAccount + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 앨리스의 설정은 아무것도 바뀌지 않았다 — 실수령액도 그대로 플레이스홀더.
        assertThat(userRepository.findById(aliceId).orElseThrow().getBaseIncome())
                .isZero();
    }

    @Test
    void 삭제된_통장은_생활비_통장으로_지정할_수_없다() throws Exception {
        long account = createAccount(aliceToken, "지울통장");
        mockMvc.perform(authed(delete("/api/v1/accounts/{id}", account), aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\",\"livingAccountId\":"
                                + account + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 생활비_통장을_지정했다가_null로_해제할_수_있다() throws Exception {
        long account = createAccount(aliceToken, "생활비통장");
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\",\"livingAccountId\":"
                                + account + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.livingAccountId").value(nullValue()));

        assertThat(userRepository.findById(aliceId).orElseThrow().getLivingAccountId())
                .isNull();
    }

    @Test
    void 월급일이_0이거나_32면_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":2500000,\"payday\":0,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":2500000,\"payday\":32,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 실수령액이_0이거나_10억을_넘으면_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":0,\"payday\":25,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":1000000001,\"payday\":25,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 미지원_조정규칙_값은_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"TOMORROW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 필수값_누락은_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(patchMe(aliceToken, "{\"payday\":25,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 언어를_en으로_바꾸면_반영되고_재조회에서_일치한다() throws Exception {
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"NONE\",\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"));

        mockMvc.perform(authed(get("/api/v1/me"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"));

        assertThat(userRepository.findById(aliceId).orElseThrow().getLocale()).isEqualTo("en");
    }

    @Test
    void locale를_생략한_PATCH는_기존_언어를_보존한다() throws Exception {
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"NONE\",\"locale\":\"en\"}"))
                .andExpect(status().isOk());

        // locale 없이 다른 설정만 갱신 — 언어는 직전 en 그대로.
        mockMvc.perform(patchMe(aliceToken, "{\"baseIncome\":3000000,\"payday\":10,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"));

        assertThat(userRepository.findById(aliceId).orElseThrow().getLocale()).isEqualTo("en");
    }

    @Test
    void 지원하지_않는_언어_코드는_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"NONE\",\"locale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 빈 문자열도 ko|en 패턴 불일치 — 거부하고 기존 언어(ko)는 불변.
        mockMvc.perform(patchMe(
                        aliceToken,
                        "{\"baseIncome\":2500000,\"payday\":25,\"paydayAdjustment\":\"NONE\",\"locale\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(userRepository.findById(aliceId).orElseThrow().getLocale()).isEqualTo("ko");
    }

    @Test
    void 토큰_없이_GET_me_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
