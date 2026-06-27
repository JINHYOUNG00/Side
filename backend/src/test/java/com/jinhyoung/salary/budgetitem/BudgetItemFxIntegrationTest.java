package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
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
 * 외화 적립 도우미(ITEM-04) 통합 테스트 — 권장 월 이체액 미리보기(저장 없음), 버퍼율 응답 노출, 입력 검증,
 * 인증을 실 PostgreSQL + 실 JWT로 검증한다. 버퍼율은 app.policy.fx-buffer-rate 기본 0.05.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BudgetItemFxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    com.jinhyoung.salary.user.infra.UserRepository userRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    JwtProvider jwtProvider;

    private String aliceToken;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        userRepository.deleteAll();
        long aliceId = userRepository
                .save(com.jinhyoung.salary.user.infra.User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        aliceToken = jwtProvider.issue(aliceId);
    }

    private String bearer() {
        return "Bearer " + aliceToken;
    }

    @Test
    void 외화_도우미는_버퍼_포함_권장월액을_저장_없이_돌려준다() throws Exception {
        // $7 × 22영업일 × ₩1,380 = 212,520 → ×1.05 = 223,146 → 1,000원 올림 → 224,000.
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":7,"frequency":"BUSINESS_DAYS","fxRate":1380}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMonthlyKrw").value(224_000))
                .andExpect(jsonPath("$.bufferRate").value(0.05));

        // 저장은 일어나지 않는다.
        assertThat(budgetItemRepository.count()).isZero();
    }

    @Test
    void 매일_빈도는_월30일로_환산한다() throws Exception {
        // $10 × 30일 × ₩1,300 = 390,000 → ×1.05 = 409,500 → 1,000원 올림 → 410,000.
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":10,"frequency":"DAILY","fxRate":1300}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMonthlyKrw").value(410_000));
    }

    @Test
    void 일금액_0은_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":0,"frequency":"DAILY","fxRate":1300}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 빈도_누락은_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":7,"fxRate":1380}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 알수없는_빈도는_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":7,"frequency":"WEEKLY","fxRate":1380}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budget-items/preview-fx")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"currency":"USD","unitAmount":7,"frequency":"BUSINESS_DAYS","fxRate":1380}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
