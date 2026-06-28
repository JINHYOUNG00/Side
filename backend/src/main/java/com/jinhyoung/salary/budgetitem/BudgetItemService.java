package com.jinhyoung.salary.budgetitem;

import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.domain.DailyInput;
import com.jinhyoung.salary.budgetitem.domain.InputCycle;
import com.jinhyoung.salary.budgetitem.domain.TaxType;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배분 항목 생성·조회 유스케이스(ITEM-01). 모든 변경·조회는 호출 사용자의 소유분에 한정한다 —
 * 소유권 검증을 이 한 곳(+리포지토리 쿼리)으로 모아 컨트롤러가 우회할 수 없게 한다(아키텍처 8장).
 *
 * <p>대상 통장(accountId)도 호출 사용자의 활성 통장이어야 한다 — 타인·삭제된 통장이면 NOT_FOUND로
 * 존재 여부를 노출하지 않는다.
 */
@Service
public class BudgetItemService {

    /** 활성 항목 개수 상한(구현규칙 6장). */
    static final long MAX_ACTIVE_ITEMS = 100;

    /** 월 환산 금액 범위(원) — 구현규칙 5장(컨트롤러 amount 검증과 동일). DAILY 환산 결과도 이 범위 안이어야 한다. */
    private static final long AMOUNT_MIN = 1;

    private static final long AMOUNT_MAX = 1_000_000_000;

    private final BudgetItemRepository budgetItemRepository;
    private final AccountRepository accountRepository;

    public BudgetItemService(BudgetItemRepository budgetItemRepository, AccountRepository accountRepository) {
        this.budgetItemRepository = budgetItemRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<BudgetItem> list(long userId) {
        return budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public BudgetItem get(long userId, long itemId) {
        return ownedActiveOrThrow(userId, itemId);
    }

    /**
     * 보관함 조회(ITEM-08) — 만기·중도해지로 보관(ARCHIVED)된 항목을 정렬 순으로 반환한다. soft delete(DELETED)와
     * 활성(ACTIVE)은 제외된다. 누적 통계(만기 수령 누적액 등)는 호출 측이 이 목록의 실수령액으로 집계한다.
     */
    @Transactional(readOnly = true)
    public List<BudgetItem> listArchived(long userId) {
        return budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ARCHIVED);
    }

    /**
     * 실수령액 기록(ITEM-08) — 만기·중도해지 시 실제로 받은 금액을 기록한다. 호출 사용자의 ACTIVE(중도해지)·
     * ARCHIVED(만기 수령) 항목만 대상이며, ACTIVE면 엔티티가 ARCHIVED로 함께 전환한다(사용자 주도, 날짜 무관).
     * DELETED·미소유·부재는 NOT_FOUND(존재 비노출). 재호출 시 값이 덮어써져 정정 가능하다.
     */
    @Transactional
    public BudgetItem recordMaturityActual(long userId, long itemId, long actualAmount) {
        BudgetItem item = budgetItemRepository
                .findByIdAndUserIdAndStatusIn(itemId, userId, List.of(ItemStatus.ACTIVE, ItemStatus.ARCHIVED))
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "budgetItem", "id", itemId)));
        item.recordMaturityActual(actualAmount);
        return item;
    }

    @Transactional
    public BudgetItem create(
            long userId,
            Category category,
            String name,
            Long monthlyAmount,
            long accountId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            InputCycle inputCycle,
            DailyInput dailyInput) {
        requireOwnedActiveAccount(userId, accountId);
        if (budgetItemRepository.countByUserIdAndStatus(userId, ItemStatus.ACTIVE) >= MAX_ACTIVE_ITEMS) {
            throw new ApiException(ErrorCode.ITEM_LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_ITEMS));
        }
        ResolvedAmount resolved = resolveAmount(inputCycle, monthlyAmount, dailyInput);
        int sortOrder = budgetItemRepository.maxSortOrder(userId) + 1;
        return budgetItemRepository.save(BudgetItem.create(
                userId,
                accountId,
                category,
                name,
                resolved.amount(),
                startDate,
                endDate,
                interestRate,
                taxType,
                expectedMaturityAmount,
                memo,
                sortOrder,
                inputCycle,
                resolved.inputMeta()));
    }

    /**
     * 항목 수정(ITEM-07). 호출 사용자의 활성 항목과 활성 대상 통장만 다룬다(미소유·비활성·부재는 NOT_FOUND로
     * 존재 비노출). 개수는 변하지 않으므로 상한 검사는 없다. 이 메서드는 budget_items 원본만 갱신한다 — 현재
     * 사이클 plan_lines에 즉시 반영하는 "이번 달 반영"은 컨트롤러가 {@code applyToCurrentCycle} 시 사이클 측
     * 재생성을 추가 호출한다(스냅샷 불변·재생성 절차는 구현규칙 4장).
     */
    @Transactional
    public BudgetItem update(
            long userId,
            long itemId,
            Category category,
            String name,
            Long monthlyAmount,
            long accountId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            InputCycle inputCycle,
            DailyInput dailyInput) {
        BudgetItem item = ownedActiveOrThrow(userId, itemId);
        requireOwnedActiveAccount(userId, accountId);
        ResolvedAmount resolved = resolveAmount(inputCycle, monthlyAmount, dailyInput);
        item.update(
                category,
                name,
                resolved.amount(),
                accountId,
                startDate,
                endDate,
                interestRate,
                taxType,
                expectedMaturityAmount,
                memo,
                inputCycle,
                resolved.inputMeta());
        return item;
    }

    /**
     * 입력 단위에 따라 저장할 월 환산 금액과 원본 보존 input_meta를 도출한다(ITEM-03). DAILY면 {@link DailyInput}이
     * 월 환산을 계산하고(자동 계산, 서버 권위) 원본 일 금액·빈도를 jsonb 맵으로 보존한다 — 환산값이 금액 범위를
     * 벗어나면 VALIDATION_FAILED. MONTHLY면 입력 금액을 그대로 쓰고 input_meta는 null이다. 입력 구조(존재·짝)
     * 검증은 컨트롤러 DTO가 먼저 거른다.
     */
    private ResolvedAmount resolveAmount(InputCycle inputCycle, Long monthlyAmount, DailyInput dailyInput) {
        if (inputCycle == InputCycle.DAILY) {
            long monthly = dailyInput.toMonthlyAmount();
            if (monthly < AMOUNT_MIN || monthly > AMOUNT_MAX) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("field", "inputMeta"));
            }
            return new ResolvedAmount(
                    monthly,
                    Map.of(
                            "dailyAmount", dailyInput.dailyAmount(),
                            "frequency", dailyInput.frequency().name()));
        }
        return new ResolvedAmount(monthlyAmount, null);
    }

    /** 저장할 월 환산 금액 + 원본 보존 input_meta(MONTHLY면 null). */
    private record ResolvedAmount(long amount, Map<String, Object> inputMeta) {}

    /**
     * 항목 soft delete(ITEM-09) — 활성 항목을 DELETED로 전환한다. 행은 잔존하며 이후 조회(목록·단건)에서
     * 제외된다. 이미 삭제·보관된 항목이나 타인·부재 항목은 NOT_FOUND(존재 비노출). 과거 스냅샷은 불변(규칙 4).
     */
    @Transactional
    public void delete(long userId, long itemId) {
        ownedActiveOrThrow(userId, itemId).markDeleted();
    }

    /** 항목 소유권 + 활성 검증의 단일 관문 — 미소유·비활성·부재는 모두 NOT_FOUND. */
    private BudgetItem ownedActiveOrThrow(long userId, long itemId) {
        return budgetItemRepository
                .findByIdAndUserIdAndStatus(itemId, userId, ItemStatus.ACTIVE)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "budgetItem", "id", itemId)));
    }

    /** 대상 통장이 호출 사용자의 활성 통장인지 검증 — 아니면 NOT_FOUND(존재 비노출). */
    private void requireOwnedActiveAccount(long userId, long accountId) {
        accountRepository
                .findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "account", "id", accountId)));
    }
}
