package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.domain.SavingsRate;
import com.jinhyoung.salary.cycle.domain.WaterfallCalculator;
import com.jinhyoung.salary.cycle.domain.WaterfallLine;
import com.jinhyoung.salary.cycle.domain.WaterfallResult;
import com.jinhyoung.salary.cycle.domain.WaterfallSplit;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폭포 조회 응답 조립(FLOW-02, API명세 3장). 호출 사용자의 실수령액·활성 항목으로 폭포를 만들어 GET
 * /me/waterfall 응답을 구성한다.
 *
 * <p>이 클래스는 <b>계산하지 않는다</b> — 그룹·소계·잔액 캐스케이드는 {@link WaterfallCalculator},
 * 비상금/생활비 분배는 {@link WaterfallSplit}(둘 다 owner의 순수 도메인, FLOW-01/03)에 위임하고, 여기서는
 * 입력 라인 구성 → 도메인 계산 호출 → 항목 메타(이름·통장)를 붙인 응답 조립만 담당한다(FLOW-02 = 조립).
 *
 * <p>봉투 적립(envelopeContribution)은 봉투 도메인(Phase 3)이 아직 없어 0을 주입한다 — 적립이 없으니
 * remaining에서 차감되는 값도 0이다.
 */
@Service
public class WaterfallQueryService {

    /** 봉투(Phase 3) 미구현 — 현재 월할 적립 합계는 없음. 봉투 도입 시 envelope 도메인이 계산해 주입한다. */
    private static final long ENVELOPE_CONTRIBUTION_UNAVAILABLE = 0L;

    /** 만기 예상금액(ITEM-05) 미구현 — 현재 항목별로 노출할 값이 없음. */
    private static final Long EXPECTED_MATURITY_UNAVAILABLE = null;

    private final UserRepository userRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final AccountRepository accountRepository;

    public WaterfallQueryService(
            UserRepository userRepository,
            BudgetItemRepository budgetItemRepository,
            AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.budgetItemRepository = budgetItemRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public WaterfallResponse getWaterfall(long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "user", "id", userId)));

        List<BudgetItem> items =
                budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE);

        // 순수 계산은 owner의 도메인에 위임 — 활성 항목을 입력 라인으로 변환해 넘기고 결과만 받는다.
        List<WaterfallLine> lines = items.stream()
                .map(item -> new WaterfallLine(item.getId(), item.getCategory(), item.getAmount()))
                .toList();
        WaterfallResult result =
                WaterfallCalculator.calculate(user.getBaseIncome(), lines, ENVELOPE_CONTRIBUTION_UNAVAILABLE);
        WaterfallSplit split = WaterfallSplit.from(result);

        // 저축률(SET-02) — 투자 포함 토글을 반영해 SavingsRate가 산정. 폭포·리포트가 같은 정의를 공유한다.
        SavingsRate savingsRate =
                SavingsRate.from(result.groups(), result.income(), user.isIncludeInvestmentInSavingsRate());

        // 응답 항목 메타 재조립: budgetItemId → 항목, accountId → 통장 별칭.
        Map<Long, BudgetItem> itemsById =
                items.stream().collect(Collectors.toMap(BudgetItem::getId, Function.identity()));
        Map<Long, String> accountNames = resolveAccountNames(items);

        List<WaterfallResponse.Group> groups = result.groups().stream()
                .map(group -> new WaterfallResponse.Group(
                        group.category(),
                        group.subtotal(),
                        group.lines().stream()
                                .map(line -> toItem(itemsById.get(line.budgetItemId()), accountNames))
                                .toList()))
                .toList();

        return new WaterfallResponse(
                result.income(),
                groups,
                result.envelopeContribution(),
                result.remaining(),
                new WaterfallResponse.Split(split.emergency(), split.living()),
                split.shortfall(),
                savingsRate);
    }

    /** 항목이 참조하는 통장들의 별칭을 한 번에 조회한다. 비활성화된 통장은 조회되지 않아 별칭이 null이 된다. */
    private Map<Long, String> resolveAccountNames(List<BudgetItem> items) {
        List<Long> accountIds =
                items.stream().map(BudgetItem::getAccountId).distinct().toList();
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
    }

    private WaterfallResponse.Item toItem(BudgetItem item, Map<Long, String> accountNames) {
        return new WaterfallResponse.Item(
                item.getId(),
                item.getName(),
                item.getAmount(),
                item.getAccountId(),
                accountNames.get(item.getAccountId()),
                item.getEndDate(),
                EXPECTED_MATURITY_UNAVAILABLE);
    }
}
