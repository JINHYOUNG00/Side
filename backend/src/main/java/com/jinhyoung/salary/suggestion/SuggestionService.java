package com.jinhyoung.salary.suggestion;

import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.checkin.infra.CheckIn;
import com.jinhyoung.salary.checkin.infra.CheckInRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.common.PolicyProperties;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.suggestion.domain.CheckInOutcome;
import com.jinhyoung.salary.suggestion.domain.MaturingItem;
import com.jinhyoung.salary.suggestion.domain.SuggestionDraft;
import com.jinhyoung.salary.suggestion.domain.SuggestionRule;
import com.jinhyoung.salary.suggestion.infra.Suggestion;
import com.jinhyoung.salary.suggestion.infra.SuggestionRepository;
import com.jinhyoung.salary.suggestion.infra.SuggestionStatus;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보정/리밸런싱 제안 유스케이스(SUG-01~03). 발동 판정·제안 산술은 순수
 * {@link com.jinhyoung.salary.suggestion.domain.SuggestionRule}이 맡고, 이 서비스는 데이터를 모아 룰에 넘기고 결과를
 * 영속화·조회·해소한다(조립 계층, ReportService와 동형). 기준일은 주입된 KST {@code Clock}으로 산출한다(규칙 3).
 *
 * <p><b>멱등</b>: 생성은 사용자별 PENDING 제안의 dedup 키 집합을 룰에 넘겨 중복을 거른다(구현규칙 7장). 같은 날
 * 배치가 다시 돌아도 새 제안이 늘지 않는다. <b>소유권</b>: 모든 조회·해소는 user_id로 게이트한다(아키텍처 8장).
 *
 * <p><b>반영(apply)의 범위</b>: payload가 제시한 금액으로 실제 배분 계획(폭포·plan_lines)을 자동 변경하지 않는다 —
 * 상태 전이(APPLIED/DISMISSED)와 해소 시각만 기록한다. 자동 반영은 폭포 도메인(소유자 영역)에 닿는 후속 작업이다.
 */
@Service
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final CycleRepository cycleRepository;
    private final CheckInRepository checkInRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;
    private final Clock clock;

    public SuggestionService(
            SuggestionRepository suggestionRepository,
            CycleRepository cycleRepository,
            CheckInRepository checkInRepository,
            BudgetItemRepository budgetItemRepository,
            UserRepository userRepository,
            PolicyProperties policy,
            Clock clock) {
        this.suggestionRepository = suggestionRepository;
        this.cycleRepository = cycleRepository;
        this.checkInRepository = checkInRepository;
        this.budgetItemRepository = budgetItemRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * 온보딩한 전 사용자에 대해 제안을 생성한다(일일 배치 단계). 온보딩 전(base_income=0) 사용자는 사이클·항목이 없어
     * 제외한다(NOTI-01과 동일). 생성된 제안 수를 반환한다.
     */
    @Transactional
    public int generateDailySuggestions() {
        int created = 0;
        for (User user : userRepository.findByBaseIncomeGreaterThan(0L)) {
            created += generateForUser(user.getId());
        }
        return created;
    }

    /**
     * 한 사용자의 제안을 생성·영속화한다(SUG-01·SUG-02). 최근 닫힌 사이클의 초과/잉여 패턴으로 RAISE_LIVING·
     * RAISE_SAVING을, 만기 도래 항목으로 REBALANCE_MATURITY를 산출하되, 같은 dedup 키의 PENDING 제안이 이미
     * 있으면 만들지 않는다(멱등). 생성된 제안 수를 반환한다.
     */
    @Transactional
    public int generateForUser(long userId) {
        LocalDate today = LocalDate.now(clock);
        int streak = policy.overspendStreak();
        Set<String> pendingKeys = pendingDedupKeys(userId);

        List<CheckInOutcome> recentClosed = recentClosedOutcomes(userId, today, streak);
        List<MaturingItem> maturing = activeMaturingItems(userId);

        List<SuggestionDraft> drafts = new ArrayList<>();
        SuggestionRule.raiseLiving(recentClosed, streak, pendingKeys).ifPresent(drafts::add);
        SuggestionRule.raiseSaving(recentClosed, streak, policy.surplusThreshold(), pendingKeys)
                .ifPresent(drafts::add);
        drafts.addAll(SuggestionRule.rebalanceMaturity(maturing, today, pendingKeys));

        for (SuggestionDraft draft : drafts) {
            suggestionRepository.save(Suggestion.create(userId, draft.type(), draft.payload()));
        }
        return drafts.size();
    }

    /**
     * 실수령액 확인 시 여윳돈/부족 배분 제안을 생성한다(CYCLE-05). 확인 실수령액이 평소 금액보다 기준 이상 많거나
     * 적으면 WINDFALL/SHORTFALL 제안을 1건 만든다(사이클당 dedup). 임계 미만이거나 같은 사이클 제안이 이미 있으면
     * 아무것도 만들지 않는다. 생성 여부를 반환한다. 호출 측(CycleIncomeService)이 실수령액 확인 흐름에서 부른다.
     */
    @Transactional
    public boolean generateWindfall(long userId, long cycleId, long baseIncome, long confirmedIncome) {
        Optional<SuggestionDraft> draft = SuggestionRule.windfall(
                cycleId, baseIncome, confirmedIncome, policy.windfallThreshold(), pendingDedupKeys(userId));
        draft.ifPresent(d -> suggestionRepository.save(Suggestion.create(userId, d.type(), d.payload())));
        return draft.isPresent();
    }

    /** 사용자에게 노출할 PENDING 제안 목록(GET /suggestions) — 최신순. */
    @Transactional(readOnly = true)
    public List<Suggestion> listPending(long userId) {
        return suggestionRepository.findByUserIdAndStatusOrderByIdDesc(userId, SuggestionStatus.PENDING);
    }

    /**
     * 제안 반영(MOD-06) — PENDING 제안을 APPLIED로 전이한다. 미소유·부재는 404(존재 비노출), 이미 해소된 제안은
     * 409 {@code SUGGESTION_ALREADY_RESOLVED}. 실제 배분 변경은 클라이언트가 payload 권고치로 편집 화면에서 수행한다.
     */
    @Transactional
    public Suggestion apply(long userId, long id) {
        Suggestion suggestion = findPending(userId, id);
        suggestion.apply(OffsetDateTime.now(clock));
        return suggestion;
    }

    /** 제안 닫기(MOD-06) — PENDING 제안을 DISMISSED로 전이한다. 게이트는 {@link #apply}와 동일. */
    @Transactional
    public Suggestion dismiss(long userId, long id) {
        Suggestion suggestion = findPending(userId, id);
        suggestion.dismiss(OffsetDateTime.now(clock));
        return suggestion;
    }

    private Suggestion findPending(long userId, long id) {
        Suggestion suggestion = suggestionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "suggestion", "id", id)));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ApiException(ErrorCode.SUGGESTION_ALREADY_RESOLVED, Map.of("id", id));
        }
        return suggestion;
    }

    /** 사용자별 PENDING 제안의 dedup 키 집합(중복 방지) — 룰이 이 키로 재생성을 거른다. */
    private Set<String> pendingDedupKeys(long userId) {
        return suggestionRepository.findByUserIdAndStatus(userId, SuggestionStatus.PENDING).stream()
                .map(SuggestionService::dedupKey)
                .collect(Collectors.toSet());
    }

    /**
     * 기존 제안의 dedup 키 — 룰이 만드는 키와 동일 규칙이어야 중복 방지가 동작한다. RAISE_*는 type 이름(사용자당
     * 하나), REBALANCE_MATURITY는 type+payload.itemId(항목별), WINDFALL/SHORTFALL은 type+payload.cycleId
     * (사이클별). jsonb 숫자는 Integer/Long으로 와도 문자열 연결 시 같은 표현이라 매칭된다.
     */
    private static String dedupKey(Suggestion suggestion) {
        return switch (suggestion.getType()) {
            case REBALANCE_MATURITY -> suggestion.getType().name() + ":"
                    + suggestion.getPayload().get("itemId");
            case WINDFALL, SHORTFALL -> suggestion.getType().name() + ":"
                    + suggestion.getPayload().get("cycleId");
            default -> suggestion.getType().name();
        };
    }

    /** 최근 닫힌 사이클 {@code streak}개를 최신→과거 순 {@link CheckInOutcome}으로(결측은 overspend=null). */
    private List<CheckInOutcome> recentClosedOutcomes(long userId, LocalDate today, int streak) {
        List<Cycle> closed = cycleRepository.findByUserIdAndCycleEndLessThanOrderByCycleStartDesc(
                userId, today, PageRequest.of(0, streak));
        if (closed.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> overspendByCycle =
                checkInRepository
                        .findByCycleIdIn(closed.stream().map(Cycle::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(CheckIn::getCycleId, CheckIn::getOverspend));
        return closed.stream()
                .map(cycle -> new CheckInOutcome(cycle.getLabel(), overspendByCycle.get(cycle.getId())))
                .toList();
    }

    /** 만기일 보유 활성 항목을 {@link MaturingItem}으로 — 예상 만기금액은 도메인 해석값(없으면 null). */
    private List<MaturingItem> activeMaturingItems(long userId) {
        return budgetItemRepository.findByUserIdAndStatusAndEndDateNotNull(userId, ItemStatus.ACTIVE).stream()
                .map(item -> new MaturingItem(
                        item.getId(),
                        item.getName(),
                        item.getAmount(),
                        item.resolveExpectedMaturityAmount(),
                        item.getEndDate()))
                .toList();
    }
}
