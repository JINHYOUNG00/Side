package com.jinhyoung.salary.cycle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import org.hamcrest.Matchers;
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
 * 폭포 조회 통합 테스트(FLOW-02). 실 PostgreSQL을 Testcontainers로 띄워 users·accounts·budget_items까지
 * 검증한다. 인증은 실제 JWT를 발급해 Bearer로 건다. 응답 조립과 함께 overAllocated(과배분) 판정을 확인한다.
 *
 * <p>첫 케이스는 노션 실데이터(income 2,473,110 → remaining 556,107, living 356,107)를 그대로 사용해 폭포
 * 골든과 같은 숫자가 HTTP 응답으로도 나오는지 교차 확인한다 — 단, 골든 파일은 건드리지 않는다(독립 입력).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WaterfallIntegrationTest {

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
    EnvelopeRepository envelopeRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        envelopeRepository.deleteAll();
        budgetItemRepository.deleteAll();
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

    private void setIncome(String token, long income) throws Exception {
        mockMvc.perform(authed(patch("/api/v1/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseIncome\":" + income + ",\"payday\":25,\"paydayAdjustment\":\"NONE\"}"))
                .andExpect(status().isOk());
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

    private void createItem(String token, String category, String name, long amount, long accountId) throws Exception {
        String body = "{\"category\":\"" + category + "\",\"name\":\"" + name + "\",\"amount\":" + amount
                + ",\"accountId\":" + accountId + ",\"startDate\":\"2026-06-01\"}";
        mockMvc.perform(authed(post("/api/v1/budget-items"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /** 봉투를 만들고 서버가 계산한 이번 사이클 월 적립액(monthlyAmount)을 돌려준다 — 폭포 차감액과 대조용. */
    private long createEnvelopeReturningMonthly(
            String token, long accountId, String name, long target, String nextDue, int cycleMonths) throws Exception {
        String body = "{\"accountId\":" + accountId + ",\"name\":\"" + name + "\",\"targetAmount\":" + target
                + ",\"nextDueDate\":\"" + nextDue + "\",\"cycleMonths\":" + cycleMonths + ",\"memo\":null}";
        String response = mockMvc.perform(authed(post("/api/v1/envelopes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("monthlyAmount").asLong();
    }

    @Test
    void 활성_봉투의_월할_적립이_envelopeContribution으로_남는돈에서_차감된다() throws Exception {
        long acct = createAccount(aliceToken, "국민");
        setIncome(aliceToken, 3_000_000);
        createItem(aliceToken, "FIXED", "월세", 500_000, acct);
        // 먼 미래 지출일이라 월 적립액은 작지만 0은 아니다 — 차감 검증의 전제.
        long monthly = createEnvelopeReturningMonthly(aliceToken, acct, "자동차세", 1_200_000, "2030-12-25", 12);

        long expectedRemaining = 3_000_000 - 500_000 - monthly; // 비상금 없음 → living = remaining
        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                // 폭포의 envelopeContribution은 봉투 목록의 월 적립액과 일치한다(같은 ENV-02 계산 재사용)·0이 아니다.
                .andExpect(jsonPath("$.envelopeContribution", Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.envelopeContribution").value((int) monthly))
                .andExpect(jsonPath("$.remaining").value((int) expectedRemaining))
                .andExpect(jsonPath("$.split.emergency").value(0))
                .andExpect(jsonPath("$.split.living").value((int) expectedRemaining))
                .andExpect(jsonPath("$.overAllocated").value(false));
    }

    @Test
    void 노션_실데이터_폭포가_응답으로_조립된다() throws Exception {
        long savings = createAccount(aliceToken, "국민");
        long etc = createAccount(aliceToken, "기타통장");
        setIncome(aliceToken, 2_473_110);
        createItem(aliceToken, "SAVING", "청년도약계좌", 700_000, savings);
        createItem(aliceToken, "INVESTMENT", "ETF", 800_000, etc);
        createItem(aliceToken, "FIXED", "월세", 310_600, etc);
        createItem(aliceToken, "INSURANCE", "실손보험", 95_653, etc);
        createItem(aliceToken, "SUBSCRIPTION", "넷플릭스", 10_750, etc);
        createItem(aliceToken, "EMERGENCY", "비상금", 200_000, etc);

        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2_473_110))
                .andExpect(jsonPath("$.envelopeContribution").value(0))
                // EMERGENCY는 groups에서 빠진다 — 표시 순서 5그룹.
                .andExpect(jsonPath("$.groups.length()").value(5))
                .andExpect(jsonPath("$.groups[0].category").value("SAVING"))
                .andExpect(jsonPath("$.groups[0].subtotal").value(700_000))
                .andExpect(jsonPath("$.groups[0].items[0].name").value("청년도약계좌"))
                .andExpect(jsonPath("$.groups[0].items[0].accountName").value("국민"))
                .andExpect(jsonPath("$.groups[0].items[0].endDate").value(Matchers.nullValue()))
                .andExpect(
                        jsonPath("$.groups[0].items[0].expectedMaturityAmount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.groups[1].category").value("INVESTMENT"))
                .andExpect(jsonPath("$.groups[3].category").value("INSURANCE"))
                .andExpect(jsonPath("$.groups[3].subtotal").value(95_653))
                .andExpect(jsonPath("$.remaining").value(556_107))
                .andExpect(jsonPath("$.split.emergency").value(200_000))
                .andExpect(jsonPath("$.split.living").value(356_107))
                .andExpect(jsonPath("$.overAllocated").value(false))
                // 저축률(SET-02) 기본값은 투자 포함 — (700,000 + 800,000) / 2,473,110 = 60.7%.
                .andExpect(jsonPath("$.savingsRate.value").value(60.7))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(true));
    }

    @Test
    void 투자_포함_토글을_끄면_저축률에서_INVESTMENT가_빠진다() throws Exception {
        long savings = createAccount(aliceToken, "국민");
        long etc = createAccount(aliceToken, "기타통장");
        setIncome(aliceToken, 2_473_110);
        createItem(aliceToken, "SAVING", "청년도약계좌", 700_000, savings);
        createItem(aliceToken, "INVESTMENT", "ETF", 800_000, etc);

        // 투자 포함 토글만 꺼서(나머지 설정은 그대로) 저축률 정의를 바꾼다.
        mockMvc.perform(authed(patch("/api/v1/me"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseIncome\":2473110,\"payday\":25,\"paydayAdjustment\":\"NONE\","
                                + "\"includeInvestmentInSavingsRate\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeInvestmentInSavingsRate").value(false));

        // 700,000 / 2,473,110 = 28.3% — 투자(800,000)는 저축액에서 빠진다.
        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsRate.value").value(28.3))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(false));
    }

    @Test
    void 비상금까지_더한_배분이_수입을_넘으면_remaining이_양수여도_overAllocated다() throws Exception {
        // remaining은 양수(100,000)지만 비상금(200,000) 배분을 더하면 생활비가 음수 → 과배분.
        long account = createAccount(aliceToken, "통장");
        setIncome(aliceToken, 1_000_000);
        createItem(aliceToken, "FIXED", "월세", 900_000, account);
        createItem(aliceToken, "EMERGENCY", "비상금", 200_000, account);

        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(100_000))
                .andExpect(jsonPath("$.split.emergency").value(200_000))
                // 생활비는 음수 그대로(clamp 금지, FLOW-03), 경고만 세운다.
                .andExpect(jsonPath("$.split.living").value(-100_000))
                .andExpect(jsonPath("$.overAllocated").value(true));
    }

    @Test
    void 항목이_없으면_빈_groups에_remaining은_income과_같다() throws Exception {
        setIncome(aliceToken, 3_000_000);

        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(3_000_000))
                .andExpect(jsonPath("$.groups.length()").value(0))
                .andExpect(jsonPath("$.remaining").value(3_000_000))
                .andExpect(jsonPath("$.split.emergency").value(0))
                .andExpect(jsonPath("$.split.living").value(3_000_000))
                .andExpect(jsonPath("$.overAllocated").value(false));
    }

    @Test
    void EMERGENCY_항목은_groups에서_빠지고_split_emergency로만_집계된다() throws Exception {
        long account = createAccount(aliceToken, "통장");
        setIncome(aliceToken, 1_000_000);
        createItem(aliceToken, "EMERGENCY", "비상금", 300_000, account);

        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(0))
                .andExpect(jsonPath("$.remaining").value(1_000_000))
                .andExpect(jsonPath("$.split.emergency").value(300_000))
                .andExpect(jsonPath("$.split.living").value(700_000))
                .andExpect(jsonPath("$.overAllocated").value(false));
    }

    @Test
    void 타인의_항목은_내_폭포에_포함되지_않는다() throws Exception {
        long aliceAccount = createAccount(aliceToken, "앨리스통장");
        setIncome(aliceToken, 2_000_000);
        createItem(aliceToken, "SAVING", "앨리스적금", 500_000, aliceAccount);

        String bobToken = jwtProvider.issue(bobId);
        long bobAccount = createAccount(bobToken, "밥통장");
        setIncome(bobToken, 9_000_000);
        createItem(bobToken, "FIXED", "밥의월세", 1_234_567, bobAccount);

        mockMvc.perform(authed(get("/api/v1/me/waterfall"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2_000_000))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].category").value("SAVING"))
                .andExpect(jsonPath("$.groups[0].subtotal").value(500_000))
                .andExpect(jsonPath("$.remaining").value(1_500_000));
    }

    @Test
    void 토큰_없이_폭포_조회는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/me/waterfall"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
