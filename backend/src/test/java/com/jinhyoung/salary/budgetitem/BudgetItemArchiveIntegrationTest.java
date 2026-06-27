package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 보관함 조회·실수령액 기록(ITEM-08) 통합 테스트. 실 PostgreSQL(Testcontainers) + 실 JWT로 보관함 목록·누적
 * 통계, 중도해지(ACTIVE→ARCHIVED) 및 만기 수령액 기록, 소유권·상태 게이트(DELETED 제외)를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BudgetItemArchiveIntegrationTest {

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
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newAccount(long userId, String name) {
        return accountRepository
                .save(Account.create(userId, name, null, null, 0))
                .getId();
    }

    private long saveItem(long userId, long accountId, String name, ItemStatus status, Long actual, int sortOrder) {
        BudgetItem item = BudgetItem.create(
                userId, accountId, Category.SAVING, name, 300000, LocalDate.of(2026, 1, 1), null, null, sortOrder);
        if (status == ItemStatus.ARCHIVED) {
            item.markArchived();
        } else if (status == ItemStatus.DELETED) {
            item.markDeleted();
        }
        if (actual != null) {
            // 보관 항목에 실수령액을 미리 심는다(recordMaturityActual은 ACTIVE면 ARCHIVED로 바꾸므로 직접 호출 회피).
            item.recordMaturityActual(actual);
        }
        return budgetItemRepository.save(item).getId();
    }

    private ItemStatus statusOf(long id) {
        return budgetItemRepository.findById(id).orElseThrow().getStatus();
    }

    private Long actualOf(long id) {
        return budgetItemRepository.findById(id).orElseThrow().getMaturityActualAmount();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String recordBody(long actualAmount) {
        return "{\"actualAmount\":" + actualAmount + "}";
    }

    @Test
    void 보관함은_ARCHIVED만_정렬순으로_내리고_활성과_삭제는_제외한다() throws Exception {
        saveItem(aliceId, aliceAccountId, "보관2", ItemStatus.ARCHIVED, 2_000_000L, 1);
        saveItem(aliceId, aliceAccountId, "보관1", ItemStatus.ARCHIVED, null, 0);
        saveItem(aliceId, aliceAccountId, "활성", ItemStatus.ACTIVE, null, 2);
        saveItem(aliceId, aliceAccountId, "삭제", ItemStatus.DELETED, null, 3);

        mockMvc.perform(authed(get("/api/v1/budget-items/archive"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("보관1"))
                .andExpect(jsonPath("$.items[1].name").value("보관2"))
                .andExpect(jsonPath("$.items[0].maturityActualAmount").isEmpty())
                .andExpect(jsonPath("$.items[1].maturityActualAmount").value(2_000_000L))
                .andExpect(jsonPath("$.items[0].expectedMaturityAmount").isEmpty());
    }

    @Test
    void 누적_통계는_보관_건수와_기록_건수_만기_수령_누적액을_집계한다() throws Exception {
        saveItem(aliceId, aliceAccountId, "수령1", ItemStatus.ARCHIVED, 1_000_000L, 0);
        saveItem(aliceId, aliceAccountId, "수령2", ItemStatus.ARCHIVED, 3_731_976L, 1);
        saveItem(aliceId, aliceAccountId, "미기록", ItemStatus.ARCHIVED, null, 2);

        mockMvc.perform(authed(get("/api/v1/budget-items/archive"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.archivedCount").value(3))
                .andExpect(jsonPath("$.stats.recordedCount").value(2))
                .andExpect(jsonPath("$.stats.totalReceivedAmount").value(4_731_976L));
    }

    @Test
    void 보관함은_타인의_보관_항목을_노출하지_않는다() throws Exception {
        long bobAccount = newAccount(bobId, "신한");
        saveItem(bobId, bobAccount, "밥의보관", ItemStatus.ARCHIVED, 9_000_000L, 0);

        mockMvc.perform(authed(get("/api/v1/budget-items/archive"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.stats.archivedCount").value(0))
                .andExpect(jsonPath("$.stats.totalReceivedAmount").value(0));
    }

    @Test
    void 활성_항목에_실수령액을_기록하면_중도해지로_ARCHIVED_전환되고_금액이_저장된다() throws Exception {
        long id = saveItem(aliceId, aliceAccountId, "중도해지대상", ItemStatus.ACTIVE, null, 0);

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(1_234_567L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.maturityActualAmount").value(1_234_567L));

        assertThat(statusOf(id)).isEqualTo(ItemStatus.ARCHIVED);
        assertThat(actualOf(id)).isEqualTo(1_234_567L);
    }

    @Test
    void 이미_보관된_항목에_기록하면_상태는_ARCHIVED_그대로이고_금액이_갱신된다() throws Exception {
        long id = saveItem(aliceId, aliceAccountId, "만기수령", ItemStatus.ARCHIVED, 1_000_000L, 0);

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(1_050_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maturityActualAmount").value(1_050_000L));

        assertThat(statusOf(id)).isEqualTo(ItemStatus.ARCHIVED);
        assertThat(actualOf(id)).isEqualTo(1_050_000L);
    }

    @Test
    void 삭제된_항목에_실수령액을_기록하면_NOT_FOUND이고_원본은_불변이다() throws Exception {
        long id = saveItem(aliceId, aliceAccountId, "삭제됨", ItemStatus.DELETED, null, 0);

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(500_000L)))
                .andExpect(status().isNotFound());

        assertThat(statusOf(id)).isEqualTo(ItemStatus.DELETED);
        assertThat(actualOf(id)).isNull();
    }

    @Test
    void 타인_항목에_실수령액을_기록하면_NOT_FOUND이고_원본은_불변이다() throws Exception {
        long bobAccount = newAccount(bobId, "신한");
        long id = saveItem(bobId, bobAccount, "밥의보관", ItemStatus.ARCHIVED, null, 0);

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(500_000L)))
                .andExpect(status().isNotFound());

        assertThat(actualOf(id)).isNull();
    }

    @Test
    void 실수령액이_0이거나_10억_초과면_검증_실패다() throws Exception {
        long id = saveItem(aliceId, aliceAccountId, "보관", ItemStatus.ARCHIVED, null, 0);

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(patch("/api/v1/budget-items/" + id + "/maturity"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(1_000_000_001L)))
                .andExpect(status().isBadRequest());

        assertThat(actualOf(id)).isNull();
    }

    @Test
    void 토큰이_없으면_보관함_조회는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/budget-items/archive")).andExpect(status().isUnauthorized());
    }
}
