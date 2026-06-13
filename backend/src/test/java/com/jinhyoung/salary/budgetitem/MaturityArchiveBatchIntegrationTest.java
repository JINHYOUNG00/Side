package com.jinhyoung.salary.budgetitem;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 만기 보관 배치(ITEM-02) 통합 테스트. 실 PostgreSQL(Testcontainers) + 고정 Clock(2026-06-13 KST)으로
 * 만기 경과 ACTIVE 항목만 ARCHIVED로 전환되는지, 재실행이 멱등인지, 다른 상태(ARCHIVED·DELETED)는
 * 건드리지 않는지를 결정론적으로 검증한다(규칙 3 Clock 주입, 규칙 8 멱등).
 */
@SpringBootTest
@Testcontainers
@Import(MaturityArchiveBatchIntegrationTest.FixedClockConfig.class)
class MaturityArchiveBatchIntegrationTest {

    /** 기준일을 2026-06-13(KST)로 고정 — now() 직접 호출 대신 주입 Clock으로 결정론 확보(규칙 3). */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 13);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MaturityArchiveService maturityArchiveService;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    UserRepository userRepository;

    private long userId;
    private long accountId;

    @BeforeEach
    void setUp() {
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        userId = userRepository
                .save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        accountId = accountRepository
                .save(Account.create(userId, "케이뱅크", null, null, 0))
                .getId();
    }

    private long saveItem(String name, LocalDate endDate, int sortOrder) {
        return budgetItemRepository
                .save(BudgetItem.create(
                        userId,
                        accountId,
                        Category.SAVING,
                        name,
                        300000,
                        LocalDate.of(2026, 1, 1),
                        endDate,
                        null,
                        sortOrder))
                .getId();
    }

    private ItemStatus statusOf(long id) {
        return budgetItemRepository.findById(id).orElseThrow().getStatus();
    }

    @Test
    void 만기_경과_ACTIVE_항목만_ARCHIVED로_전환되고_경계_당일과_미래_무만기는_그대로다() {
        long matured = saveItem("만기경과", TODAY.minusDays(1), 0);
        long dueToday = saveItem("만기당일", TODAY, 1);
        long future = saveItem("미래만기", TODAY.plusDays(30), 2);
        long noEnd = saveItem("무만기", null, 3);

        int archived = maturityArchiveService.archiveMaturedItems();

        assertThat(archived).isEqualTo(1);
        assertThat(statusOf(matured)).isEqualTo(ItemStatus.ARCHIVED);
        assertThat(statusOf(dueToday)).isEqualTo(ItemStatus.ACTIVE);
        assertThat(statusOf(future)).isEqualTo(ItemStatus.ACTIVE);
        assertThat(statusOf(noEnd)).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void 같은_날_재실행하면_전환_건수가_0이고_상태가_불변이다_멱등() {
        long matured = saveItem("만기경과", TODAY.minusDays(1), 0);

        assertThat(maturityArchiveService.archiveMaturedItems()).isEqualTo(1);
        assertThat(maturityArchiveService.archiveMaturedItems()).isEqualTo(0);
        assertThat(statusOf(matured)).isEqualTo(ItemStatus.ARCHIVED);
    }

    @Test
    void 보관된_항목은_활성_목록_조회에서_제외된다() {
        saveItem("만기경과", TODAY.minusDays(1), 0);
        saveItem("정상", TODAY.plusDays(30), 1);

        maturityArchiveService.archiveMaturedItems();

        assertThat(budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE))
                .extracting(BudgetItem::getName)
                .containsExactly("정상");
    }

    @Test
    void 이미_ARCHIVED나_DELETED인_만기_경과_항목은_배치가_건드리지_않는다() {
        long alreadyArchived = saveItem("이미보관", TODAY.minusDays(10), 0);
        BudgetItem toArchive = budgetItemRepository.findById(alreadyArchived).orElseThrow();
        toArchive.markArchived();
        budgetItemRepository.save(toArchive);

        long deleted = saveItem("삭제됨", TODAY.minusDays(10), 1);
        BudgetItem toDelete = budgetItemRepository.findById(deleted).orElseThrow();
        toDelete.markDeleted();
        budgetItemRepository.save(toDelete);

        int archived = maturityArchiveService.archiveMaturedItems();

        assertThat(archived).isZero();
        assertThat(statusOf(alreadyArchived)).isEqualTo(ItemStatus.ARCHIVED);
        assertThat(statusOf(deleted)).isEqualTo(ItemStatus.DELETED);
    }
}
