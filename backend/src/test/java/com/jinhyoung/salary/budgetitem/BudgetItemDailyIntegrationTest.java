package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.InputCycle;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
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
 * 일 단위 입력(ITEM-03) 통합 테스트 — 월 환산 미리보기(저장 없음), DAILY 생성 시 amount 자동 계산·input_cycle·
 * input_meta(jsonb) 왕복 보존, MONTHLY 기본 하위호환, 입력 단위↔금액 짝 검증, 환산값 범위, 수정 시 단위 전환,
 * 인증을 실 PostgreSQL + 실 JWT로 검증한다. 월 평균 일수는 DAILY 30 / BUSINESS_DAYS 22(FxFrequency 공유).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BudgetItemDailyIntegrationTest {

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

    private String aliceToken;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        long aliceId = userRepository
                .save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        aliceToken = jwtProvider.issue(aliceId);
        aliceAccountId = accountRepository
                .save(Account.create(aliceId, "케이뱅크", null, null, 0))
                .getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken);
    }

    // ── 미리보기(저장 없음) ──────────────────────────────────────────────

    @Test
    void 미리보기는_매일_빈도를_월30일로_환산하고_저장하지_않는다() throws Exception {
        // 10,000원 × 30일 = 300,000원.
        mockMvc.perform(authed(post("/api/v1/budget-items/preview-daily"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyAmount\":10000,\"frequency\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyAmount").value(300_000));

        assertThat(budgetItemRepository.count()).isZero();
    }

    @Test
    void 미리보기는_영업일_빈도를_월22일로_환산한다() throws Exception {
        // 10,000원 × 22영업일 = 220,000원.
        mockMvc.perform(authed(post("/api/v1/budget-items/preview-daily"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyAmount\":10000,\"frequency\":\"BUSINESS_DAYS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyAmount").value(220_000));
    }

    @Test
    void 미리보기_일금액_0은_400() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items/preview-daily"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyAmount\":0,\"frequency\":\"DAILY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 미리보기_빈도_누락은_400() throws Exception {
        mockMvc.perform(authed(post("/api/v1/budget-items/preview-daily"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyAmount\":10000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미리보기_토큰_없으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/budget-items/preview-daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyAmount\":10000,\"frequency\":\"DAILY\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 생성: 일 단위 입력 → 월 환산 저장 + 원본 보존 ──────────────────────

    @Test
    void DAILY_생성하면_월환산_amount가_저장되고_input_meta가_왕복_보존된다() throws Exception {
        // 일 10,000원 × 매일 30일 = 월 300,000원. amount는 서버가 계산, 원본은 input_meta로 보존.
        String body = "{\"category\":\"SAVING\",\"name\":\"매일적금\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\",\"inputCycle\":\"DAILY\","
                + "\"inputMeta\":{\"dailyAmount\":10000,\"frequency\":\"DAILY\"}}";
        String response = mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300_000))
                .andExpect(jsonPath("$.inputCycle").value("DAILY"))
                .andExpect(jsonPath("$.inputMeta.dailyAmount").value(10000))
                .andExpect(jsonPath("$.inputMeta.frequency").value("DAILY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        BudgetItem saved = budgetItemRepository.findById(id).orElseThrow();
        assertThat(saved.getAmount()).isEqualTo(300_000);
        assertThat(saved.getInputCycle()).isEqualTo(InputCycle.DAILY);
        assertThat(saved.getInputMeta()).containsEntry("frequency", "DAILY");
        assertThat(((Number) saved.getInputMeta().get("dailyAmount")).longValue())
                .isEqualTo(10_000);

        // 조회(GET)에서도 원본이 프리필용으로 실려 온다.
        mockMvc.perform(authed(get("/api/v1/budget-items")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inputCycle").value("DAILY"))
                .andExpect(jsonPath("$[0].inputMeta.dailyAmount").value(10000));
    }

    @Test
    void MONTHLY_기본_생성은_input_cycle_MONTHLY_input_meta_null이다() throws Exception {
        // inputCycle 미지정(하위호환) → MONTHLY, input_meta는 null.
        String body = "{\"category\":\"FIXED\",\"name\":\"월세\",\"amount\":500000,\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\"}";
        String response = mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(500_000))
                .andExpect(jsonPath("$.inputCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.inputMeta").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        BudgetItem saved = budgetItemRepository.findById(id).orElseThrow();
        assertThat(saved.getInputCycle()).isEqualTo(InputCycle.MONTHLY);
        assertThat(saved.getInputMeta()).isNull();
    }

    @Test
    void DAILY인데_input_meta가_없으면_400() throws Exception {
        String body = "{\"category\":\"SAVING\",\"name\":\"매일적금\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\",\"inputCycle\":\"DAILY\"}";
        mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void MONTHLY인데_amount가_없으면_400() throws Exception {
        String body = "{\"category\":\"FIXED\",\"name\":\"월세\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\"}";
        mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void DAILY_환산값이_10억을_초과하면_400() throws Exception {
        // 일 40,000,000원 × 30일 = 12억 > 10억 상한 → 서비스가 VALIDATION_FAILED.
        String body = "{\"category\":\"SAVING\",\"name\":\"과대적금\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\",\"inputCycle\":\"DAILY\","
                + "\"inputMeta\":{\"dailyAmount\":40000000,\"frequency\":\"DAILY\"}}";
        mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(budgetItemRepository.count()).isZero();
    }

    // ── 수정: 단위 전환 ─────────────────────────────────────────────────

    @Test
    void 수정으로_MONTHLY를_DAILY로_바꾸면_amount_재계산되고_input_meta가_채워진다() throws Exception {
        String createBody = "{\"category\":\"INVESTMENT\",\"name\":\"적립\",\"amount\":500000,\"accountId\":"
                + aliceAccountId + ",\"startDate\":\"2026-07-01\"}";
        String created = mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // 일 5,000원 × 영업일 22 = 110,000원.
        String updateBody = "{\"category\":\"INVESTMENT\",\"name\":\"적립\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\",\"inputCycle\":\"DAILY\","
                + "\"inputMeta\":{\"dailyAmount\":5000,\"frequency\":\"BUSINESS_DAYS\"}}";
        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(110_000))
                .andExpect(jsonPath("$.inputCycle").value("DAILY"))
                .andExpect(jsonPath("$.inputMeta.dailyAmount").value(5000))
                .andExpect(jsonPath("$.inputMeta.frequency").value("BUSINESS_DAYS"));
    }

    @Test
    void 수정으로_DAILY를_MONTHLY로_바꾸면_input_meta가_비워진다() throws Exception {
        String createBody = "{\"category\":\"SAVING\",\"name\":\"매일적금\",\"accountId\":" + aliceAccountId
                + ",\"startDate\":\"2026-07-01\",\"inputCycle\":\"DAILY\","
                + "\"inputMeta\":{\"dailyAmount\":10000,\"frequency\":\"DAILY\"}}";
        String created = mockMvc.perform(authed(post("/api/v1/budget-items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        String updateBody = "{\"category\":\"SAVING\",\"name\":\"매일적금\",\"amount\":250000,\"accountId\":"
                + aliceAccountId + ",\"startDate\":\"2026-07-01\"}";
        String updated = mockMvc.perform(authed(patch("/api/v1/budget-items/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250_000))
                .andExpect(jsonPath("$.inputCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.inputMeta").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long updatedId = objectMapper.readTree(updated).get("id").asLong();
        assertThat(budgetItemRepository.findById(updatedId).orElseThrow().getInputMeta())
                .isNull();
    }
}
