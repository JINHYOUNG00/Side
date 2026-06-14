package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.domain.CycleSnapshotBuilder;
import com.jinhyoung.salary.cycle.domain.PlanLineDraft;
import com.jinhyoung.salary.cycle.domain.WaterfallLine;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이클 스냅샷 영속화(CYCLE-03). 지급일에 사용자의 활성 항목·설정으로 {@code cycles}·{@code plan_lines}
 * 계획 스냅샷을 한 번 박는다.
 *
 * <p>이 서비스는 <b>계산하지 않는다</b> — 경계·라벨은 {@link CycleService}(CYCLE-02)에, plan_line 구성은
 * owner의 순수 {@link CycleSnapshotBuilder}(FLOW-03)에 위임하고, 여기서는 그 산출물({@link PlanLineDraft})을
 * 항목·통장으로 해석해 이름·카테고리·통장 별칭 스냅샷을 채워 영속화하는 application 경계만 맡는다
 * (WaterfallQueryService가 응답 메타를 재조립하는 것과 같은 분담).
 *
 * <p><b>멱등</b>(규칙 8, NFR-05): 같은 {@code (user_id, cycle_start)}로 다시 호출되면 이미 생성된 사이클을
 * 그대로 돌려주고 아무것도 적재하지 않는다. 존재 확인({@code existsByUserIdAndCycleStart})이 정상 경로의
 * 게이트이며, 경합·다중 실행 시에는 cycles의 {@code unique(user_id, cycle_start)} 제약이 최종 가드다.
 *
 * <p>실제 지급일 판정으로 어느 달 사이클인지를 정해 이 서비스를 호출하는 일일 트리거(@Scheduled)는 별도
 * 후속 작업이다 — NOTI-01의 지급일 일일 판정을 재사용해 스냅샷→알림 순서로 묶을 수 있다(아키텍처 4장).
 */
@Service
public class CycleSnapshotService {

    /** 봉투(Phase 3) 미구현 — 월할 적립 합계는 아직 0. 봉투 도입 시 envelope 도메인이 계산해 주입한다. */
    private static final long ENVELOPE_CONTRIBUTION_UNAVAILABLE = 0L;

    private final UserRepository userRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final AccountRepository accountRepository;
    private final CycleService cycleService;
    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;

    public CycleSnapshotService(
            UserRepository userRepository,
            BudgetItemRepository budgetItemRepository,
            AccountRepository accountRepository,
            CycleService cycleService,
            CycleRepository cycleRepository,
            PlanLineRepository planLineRepository) {
        this.userRepository = userRepository;
        this.budgetItemRepository = budgetItemRepository;
        this.accountRepository = accountRepository;
        this.cycleService = cycleService;
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
    }

    /**
     * 시작 월 사이클의 계획 스냅샷을 영속화한다. 이미 존재하면 멱등 스킵(기존 사이클 반환).
     *
     * @param userId 대상 사용자
     * @param startMonth 어느 달 월급 사이클인지(트리거가 실지급일 판정으로 결정해 넘긴다)
     * @return 새로 만든 또는 이미 존재하던 사이클 헤더
     */
    @Transactional
    public Cycle createSnapshot(long userId, YearMonth startMonth) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "user", "id", userId)));

        CycleDefinition definition =
                cycleService.resolveCycle(startMonth, user.getPayday(), user.getPaydayAdjustment());

        // 멱등 게이트(정상 경로) — 같은 사이클이 이미 있으면 그대로 돌려주고 적재하지 않는다.
        return cycleRepository
                .findByUserIdAndCycleStart(userId, definition.cycleStart())
                .orElseGet(() -> persist(user, definition));
    }

    private Cycle persist(User user, CycleDefinition definition) {
        // 활성 항목(EMERGENCY 포함)을 sort_order 순으로 — WaterfallQueryService와 동일 변환.
        List<BudgetItem> items =
                budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(user.getId(), ItemStatus.ACTIVE);
        List<WaterfallLine> lines = items.stream()
                .map(item -> new WaterfallLine(item.getId(), item.getCategory(), item.getAmount()))
                .toList();

        // plan_line 구성은 owner 순수 도메인에 위임(계산 0줄). LIVING 생성 조건도 빌더가 판단한다.
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(
                user.getBaseIncome(), lines, ENVELOPE_CONTRIBUTION_UNAVAILABLE, user.getLivingAccountId());

        // 사이클 헤더 먼저(plan_lines.cycle_id FK). income 기본값=평소 실수령액, income_confirmed=false(CYCLE-04).
        Cycle cycle = cycleRepository.save(Cycle.create(user.getId(), definition, user.getBaseIncome()));

        Map<Long, BudgetItem> itemsById =
                items.stream().collect(Collectors.toMap(BudgetItem::getId, Function.identity()));
        Map<Long, String> accountNames = resolveAccountNames(items, user.getLivingAccountId());

        List<PlanLine> planLines = new ArrayList<>(drafts.size());
        for (PlanLineDraft draft : drafts) {
            planLines.add(toPlanLine(cycle.getId(), draft, itemsById, accountNames));
        }
        planLineRepository.saveAll(planLines);
        return cycle;
    }

    /** draft(계산 산출물)를 항목·통장으로 해석해 이름·카테고리·통장 별칭 스냅샷을 채운 plan_line으로 만든다. */
    private PlanLine toPlanLine(
            Long cycleId, PlanLineDraft draft, Map<Long, BudgetItem> itemsById, Map<Long, String> accountNames) {
        return switch (draft.lineType()) {
            case ITEM -> {
                BudgetItem item = itemsById.get(draft.budgetItemId());
                yield PlanLine.item(
                        cycleId,
                        item.getId(),
                        item.getAccountId(),
                        item.getName(),
                        draft.category().name(),
                        accountName(accountNames, item.getAccountId()),
                        draft.plannedAmount());
            }
            case LIVING -> PlanLine.living(
                    cycleId, draft.accountId(), accountName(accountNames, draft.accountId()), draft.plannedAmount());
            default -> throw new IllegalStateException("FLOW-03 빌더는 ITEM·LIVING만 산출한다: " + draft.lineType());
        };
    }

    /**
     * 항목의 대상 통장과 생활비 통장 별칭을 한 번에 조회한다. soft delete(is_active=false)된 통장도 행은
     * 남아 별칭을 조회할 수 있다(ERD — plan_lines가 참조 유지). 생활비 통장은 항목이 없을 수 있어 따로 더한다.
     */
    private Map<Long, String> resolveAccountNames(List<BudgetItem> items, Long livingAccountId) {
        List<Long> accountIds = new ArrayList<>(
                items.stream().map(BudgetItem::getAccountId).distinct().toList());
        if (livingAccountId != null && !accountIds.contains(livingAccountId)) {
            accountIds.add(livingAccountId);
        }
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
    }

    /**
     * 통장 별칭을 스냅샷에 박는다. account_name_snapshot은 NOT NULL이라 통장을 못 찾으면(탈퇴 cascade로 물리
     * 삭제된 경우뿐 — 정상 흐름에선 발생하지 않음) 손상된 스냅샷을 남기지 않고 불변식 위반으로 끊는다.
     */
    private String accountName(Map<Long, String> accountNames, Long accountId) {
        String name = accountNames.get(accountId);
        if (name == null) {
            throw new IllegalStateException("스냅샷 대상 통장 별칭을 찾을 수 없다: accountId=" + accountId);
        }
        return name;
    }
}
