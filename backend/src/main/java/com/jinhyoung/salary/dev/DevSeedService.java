package com.jinhyoung.salary.dev;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 전용 노션 시드(VERIFY-notion-match). {@code dev} 프로필에서만 빈으로 등록되므로 운영 프로필엔 존재하지 않는다.
 *
 * <p>폭포 골든 fixture와 동일한 노션 실데이터를 적재한다(income 2,473,110 → 생활비 LIVING 356,107): 통장 3개
 * (국민·기타통장·생활비통장)에 항목 6건 + 생활비 통장 지정. 적재는 멱등하다(규칙 8) — (provider=DEV,
 * provider_id=notion) 사용자가 이미 있으면 재사용하고 통장·항목을 다시 만들지 않는다. 날짜는 리터럴이라
 * {@code LocalDate.now()} 직접 호출 금지(규칙 3)에 걸리지 않는다.
 */
@Service
@Profile("dev")
public class DevSeedService {

    /** dev 시드 사용자 식별자 — OAuth 공급자와 충돌하지 않는 전용 값. 멱등 게이트 키. */
    static final String PROVIDER = "DEV";

    static final String PROVIDER_ID = "notion";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BudgetItemRepository budgetItemRepository;

    public DevSeedService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            BudgetItemRepository budgetItemRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.budgetItemRepository = budgetItemRepository;
    }

    /** 노션 시드 사용자를 보장하고 그 id를 반환한다. 이미 있으면 그대로 둔다(멱등 — 규칙 8). */
    @Transactional
    public long ensureNotionUser() {
        return userRepository
                .findByProviderAndProviderId(PROVIDER, PROVIDER_ID)
                .map(User::getId)
                .orElseGet(this::seed);
    }

    private long seed() {
        User user = userRepository.save(User.createFromOAuth(PROVIDER, PROVIDER_ID, "dev@notion.local", "노션"));
        long userId = user.getId();

        long kookmin = accountRepository
                .save(Account.create(userId, "국민", null, null, 0))
                .getId();
        long etc = accountRepository
                .save(Account.create(userId, "기타통장", null, null, 1))
                .getId();
        long living = accountRepository
                .save(Account.create(userId, "생활비통장", null, null, 2))
                .getId();

        LocalDate start = LocalDate.of(2026, 6, 1);
        budgetItemRepository.save(
                BudgetItem.create(userId, kookmin, Category.SAVING, "청년도약계좌", 700_000, start, null, null, 0));
        budgetItemRepository.save(
                BudgetItem.create(userId, etc, Category.INVESTMENT, "ETF", 800_000, start, null, null, 1));
        budgetItemRepository.save(BudgetItem.create(userId, etc, Category.FIXED, "월세", 310_600, start, null, null, 2));
        budgetItemRepository.save(
                BudgetItem.create(userId, etc, Category.INSURANCE, "실손보험", 95_653, start, null, null, 3));
        budgetItemRepository.save(
                BudgetItem.create(userId, etc, Category.SUBSCRIPTION, "넷플릭스", 10_750, start, null, null, 4));
        budgetItemRepository.save(
                BudgetItem.create(userId, etc, Category.EMERGENCY, "비상금", 200_000, start, null, null, 5));

        user.updateSettings(2_473_110, (short) 25, PaydayAdjustment.NONE, living);
        userRepository.save(user);
        return userId;
    }
}
