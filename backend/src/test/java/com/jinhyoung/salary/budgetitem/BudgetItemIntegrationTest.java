package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
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
 * 배분 항목 생성·조회 + 소유권 검증 통합 테스트(ITEM-01). 실 PostgreSQL을 Testcontainers로 띄워
 * Flyway V1(budget_items·accounts·users FK 포함)까지 함께 검증한다. 인증은 실제 JWT를 발급해 Bearer로 건다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BudgetItemIntegrationTest {

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
    BudgetItemRepository budgetItemRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        aliceId = newUser("alice");
        bobId = newUser("bob");
        aliceToken = jwtProvider.issue(aliceId);
        aliceAccountId = newAccount(aliceId, "케이뱅크");
    }

    private long newUser(String key) {
        User user = userRepository.save(User.createFromOAuth("KAKAO", key, key + "@x.com", key));
        return user.getId();
    }

    private long newAccount(long userId, String name) {
        return accountRepository
                .save(Account.create(userId, name, null, null, 0))
                .getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String createItemBody(String category, String name, long amount, long accountId) {
        return "{\"category\":\"" + category + "\",\"name\":\"" + name + "\",\"amount\":" + amount + ",\"accountId\":"
                + accountId + ",\"startDate\":\"2026-07-01\"}";
    }

    private long createItem(String token, String category, String name, long amount, long accountId) throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/budget-items"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody(category, name, amount, accountId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void 항목을_생성하면_필드가_저장되고_sortOrder가_끝자리로_부여된다() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "청년적금", 300000, aliceAccountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SAVING"))
                .andExpect(jsonPath("$.name").value("청년적금"))
                .andExpect(jsonPath("$.amount").value(300000))
                .andExpect(jsonPath("$.accountId").value(aliceAccountId))
                .andExpect(jsonPath("$.startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.sortOrder").value(0));

        createItem(aliceToken, "FIXED", "월세", 500000, aliceAccountId);

        mockMvc.perform(authed(get("/api/v1/budget-items"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("청년적금"))
                .andExpect(jsonPath("$[1].name").value("월세"))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
    }

    @Test
    void 단건_조회는_소유한_활성_항목을_돌려준다() throws Exception {
        long id = createItem(aliceToken, "INVESTMENT", "ETF적립", 200000, aliceAccountId);

        mockMvc.perform(authed(get("/api/v1/budget-items/{id}", id), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("ETF적립"));
    }

    @Test
    void 다른_사용자의_항목은_목록에_보이지_않는다() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        createItem(jwtProvider.issue(bobId), "SAVING", "밥의적금", 100000, bobAccount);

        mockMvc.perform(authed(get("/api/v1/budget-items"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 다른_사용자의_항목은_단건_조회할_수_없다_소유권_위반_차단() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        long bobItem = createItem(jwtProvider.issue(bobId), "SAVING", "밥의적금", 100000, bobAccount);

        mockMvc.perform(authed(get("/api/v1/budget-items/{id}", bobItem), aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 타인의_통장을_대상으로_항목을_만들_수_없다_NOT_FOUND() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");

        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "탈취적금", 100000, bobAccount)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 앨리스 쪽엔 아무 항목도 생기지 않았다.
        assertThat(budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(
                        aliceId, com.jinhyoung.salary.budgetitem.infra.ItemStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void 없는_통장을_대상으로_하면_NOT_FOUND() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "유령적금", 100000, 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void LIVING_카테고리는_항목으로_쓸_수_없다_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("LIVING", "생활비", 100000, aliceAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 금액이_0이면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "공짜", 0, aliceAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 금액이_10억을_넘으면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "거액", 1_000_000_001L, aliceAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 이름이_비면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("SAVING", "", 100000, aliceAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 활성_항목이_100개면_추가_생성은_409_ITEM_LIMIT_EXCEEDED다() throws Exception {
        for (int i = 0; i < 100; i++) {
            createItem(aliceToken, "FIXED", "항목" + i, 1000, aliceAccountId);
        }

        mockMvc.perform(authed(post("/api/v1/budget-items"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody("FIXED", "백한번째", 1000, aliceAccountId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_LIMIT_EXCEEDED"));
    }

    @Test
    void 토큰_없이_항목_목록_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/budget-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
