package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.domain.TaxType;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 적금 만기금액(ITEM-05/06) 통합 테스트 — 만기금액 미리보기(저장 없음), 저축 조건부 필드(이율·세금·예상금액
 * 수동값) 생성·조회 영속, 보관함의 "예상 vs 실제" 해석값(수동 우선·없으면 공식)을 실 PostgreSQL + 실 JWT로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BudgetItemMaturityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    com.jinhyoung.salary.user.infra.UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private String aliceToken;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        aliceId = userRepository
                .save(com.jinhyoung.salary.user.infra.User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        aliceToken = jwtProvider.issue(aliceId);
        aliceAccountId = accountRepository
                .save(Account.create(aliceId, "케이뱅크", null, null, 0))
                .getId();
    }

    private String bearer() {
        return "Bearer " + aliceToken;
    }

    // ── 미리보기(ITEM-05) ───────────────────────────────────────────────────

    @Test
    void 만기금액_미리보기는_골든_분해값을_저장_없이_돌려준다() throws Exception {
        // 골든 적금A: 월 30만 · 12개월 · 연 8% · 일반과세 → 원금 3,600,000 · 이자 156,000 · 세금 24,024 · 만기 3,731,976.
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-maturity")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"monthlyAmount":300000,"months":12,"interestRate":8.0,"taxType":"NORMAL_15_4"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(3_600_000))
                .andExpect(jsonPath("$.interest").value(156_000))
                .andExpect(jsonPath("$.tax").value(24_024))
                .andExpect(jsonPath("$.total").value(3_731_976));

        // 저장은 일어나지 않는다.
        assertThat(budgetItemRepository.count()).isZero();
    }

    @Test
    void 세금우대_미리보기는_농특세_1_4퍼센트만_적용한다() throws Exception {
        // 월 10만 · 12개월 · 8% · 세금우대: 이자 52,000 · 세금 728 · 만기 1,251,272.
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-maturity")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"monthlyAmount":100000,"months":12,"interestRate":8.0,"taxType":"PREFERENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interest").value(52_000))
                .andExpect(jsonPath("$.tax").value(728))
                .andExpect(jsonPath("$.total").value(1_251_272));
    }

    @Test
    void 미리보기_개월수_0은_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-maturity")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"monthlyAmount":300000,"months":0,"interestRate":8.0,"taxType":"NORMAL_15_4"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 미리보기_세금유형_누락은_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-maturity")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"monthlyAmount":300000,"months":12,"interestRate":8.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미리보기_토큰_없으면_401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-maturity")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"monthlyAmount":300000,"months":12,"interestRate":8.0,"taxType":"NORMAL_15_4"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 저축 조건부 필드 생성·조회(ITEM-05/06) ─────────────────────────────

    @Test
    void 저축_항목_생성시_이율과_세금유형이_영속되고_응답에_노출된다() throws Exception {
        mockMvc.perform(post("/api/v1/budget-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"category":"SAVING","name":"OO적금","amount":300000,"accountId":%d,
                                 "startDate":"2026-07-01","endDate":"2027-06-30",
                                 "interestRate":8.0,"taxType":"NORMAL_15_4"}
                                """
                                        .formatted(aliceAccountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interestRate").value(8.0))
                .andExpect(jsonPath("$.taxType").value("NORMAL_15_4"))
                .andExpect(jsonPath("$.expectedMaturityAmount").doesNotExist());

        BudgetItem saved = budgetItemRepository.findAll().get(0);
        assertThat(saved.getInterestRate()).isEqualByComparingTo("8.0");
        assertThat(saved.getTaxType()).isEqualTo(TaxType.NORMAL_15_4);
        assertThat(saved.getExpectedMaturityAmount()).isNull();
    }

    @Test
    void 특수상품_수동_예상금액이_원본값으로_영속되고_응답에_노출된다() throws Exception {
        // ITEM-06: 청년도약계좌 등. expectedMaturityAmount 수동 입력값은 BudgetItemResponse에 원본 그대로.
        mockMvc.perform(post("/api/v1/budget-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"category":"SAVING","name":"청년도약계좌","amount":700000,"accountId":%d,
                                 "startDate":"2026-07-01","endDate":"2031-06-30",
                                 "expectedMaturityAmount":50000000}
                                """
                                        .formatted(aliceAccountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedMaturityAmount").value(50_000_000));

        BudgetItem saved = budgetItemRepository.findAll().get(0);
        assertThat(saved.getExpectedMaturityAmount()).isEqualTo(50_000_000L);
    }

    @Test
    void 이율_100초과는_400() throws Exception {
        mockMvc.perform(post("/api/v1/budget-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"category":"SAVING","name":"OO적금","amount":300000,"accountId":%d,
                                 "startDate":"2026-07-01","endDate":"2027-06-30","interestRate":150.0,"taxType":"NORMAL_15_4"}
                                """
                                        .formatted(aliceAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── 보관함 "예상 vs 실제" 해석값(ITEM-05/06 → SCR-08) ──────────────────

    @Test
    void 보관함은_저축_항목의_예상_만기금액을_공식으로_해석해_내린다() throws Exception {
        long id = saveArchivedSaving("만기적금", new BigDecimal("8.0"), TaxType.NORMAL_15_4, null);

        mockMvc.perform(get("/api/v1/budget-items/archive").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value((int) id))
                .andExpect(jsonPath("$.items[0].expectedMaturityAmount").value(3_731_976));
    }

    @Test
    void 보관함은_수동_입력값이_있으면_공식_대신_그_값을_내린다() throws Exception {
        saveArchivedSaving("청년도약", new BigDecimal("8.0"), TaxType.NORMAL_15_4, 50_000_000L);

        mockMvc.perform(get("/api/v1/budget-items/archive").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].expectedMaturityAmount").value(50_000_000));
    }

    /** 보관(ARCHIVED) 상태의 저축 항목을 직접 적재 — 월 30만·시작 2026-07-01·만기 2027-06-30(12개월). */
    private long saveArchivedSaving(String name, BigDecimal rate, TaxType taxType, Long manual) {
        BudgetItem item = BudgetItem.create(
                aliceId,
                aliceAccountId,
                Category.SAVING,
                name,
                300_000,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30),
                rate,
                taxType,
                manual,
                null,
                0);
        item.markArchived();
        return budgetItemRepository.save(item).getId();
    }
}
