package com.jinhyoung.salary.dataexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.dataexport.ImportSpecParser.ParsedRow;
import com.jinhyoung.salary.dataexport.domain.ExportFormat;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 데이터 내보내기 통합 테스트(DATA-02). 실 PostgreSQL(Testcontainers)에 활성·보관·삭제 항목과 타 사용자 항목을
 * 심고 {@code GET /export}가 본인 활성 항목만, 올바른 포맷으로 내려주는지 검증한다. 핵심은 라운드트립 —
 * 내려받은 텍스트를 임포트 스펙({@link ImportSpecParser})으로 되읽으면 이름·금액이 그대로 복원된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DataExportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    JwtProvider jwtProvider;

    private String aliceToken;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        long aliceId = newUser("alice");
        aliceToken = jwtProvider.issue(aliceId);
        long aliceAccountId = newAccount(aliceId, "케이뱅크");

        saveItem(aliceId, aliceAccountId, Category.FIXED, "월세", 500_000, 0, Status.ACTIVE);
        saveItem(aliceId, aliceAccountId, Category.SAVING, "OO적금", 300_000, 1, Status.ACTIVE);
        // 삭제·보관 항목과 타인 항목은 내보내기에 들어가면 안 된다.
        saveItem(aliceId, aliceAccountId, Category.FIXED, "삭제됨", 111_000, 2, Status.DELETED);
        saveItem(aliceId, aliceAccountId, Category.SAVING, "보관됨", 222_000, 3, Status.ARCHIVED);

        long bobId = newUser("bob");
        long bobAccountId = newAccount(bobId, "토스뱅크");
        saveItem(bobId, bobAccountId, Category.FIXED, "밥월세", 999_000, 0, Status.ACTIVE);
    }

    @Test
    void 마크다운으로_본인_활성항목만_내려주고_임포트로_라운드트립한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/export")
                        .param("format", "md")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/markdown");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("salary-export.md");

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 활성 항목만, 삭제·보관·타인 항목 제외.
        assertThat(body).contains("월세", "OO적금", "FIXED", "SAVING", "500000", "300000");
        assertThat(body).doesNotContain("삭제됨", "보관됨", "밥월세", "999000");

        // 라운드트립: 내보내기 → 임포트 스펙 파싱 → 이름·금액이 정렬 순으로 동일.
        assertThat(ImportSpecParser.parse(body, ExportFormat.MARKDOWN))
                .containsExactly(new ParsedRow("월세", 500_000), new ParsedRow("OO적금", 300_000));
    }

    @Test
    void CSV로_본인_활성항목만_내려주고_임포트로_라운드트립한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/export")
                        .param("format", "csv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/csv");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("salary-export.csv");

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("밥월세", "삭제됨", "보관됨");

        assertThat(ImportSpecParser.parse(body, ExportFormat.CSV))
                .containsExactly(new ParsedRow("월세", 500_000), new ParsedRow("OO적금", 300_000));
    }

    @Test
    void 미지원_포맷은_400_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(get("/api/v1/export")
                        .param("format", "xml")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/export").param("format", "md")).andExpect(status().isUnauthorized());
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newAccount(long userId, String name) {
        return accountRepository
                .save(Account.create(userId, name, null, null, 0))
                .getId();
    }

    private enum Status {
        ACTIVE,
        ARCHIVED,
        DELETED
    }

    private void saveItem(
            long userId, long accountId, Category category, String name, long amount, int sortOrder, Status status) {
        BudgetItem item = BudgetItem.create(
                userId, accountId, category, name, amount, LocalDate.of(2026, 1, 1), null, null, sortOrder);
        if (status == Status.ARCHIVED) {
            item.markArchived();
        } else if (status == Status.DELETED) {
            item.markDeleted();
        }
        budgetItemRepository.save(item);
    }
}
