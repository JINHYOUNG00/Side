package com.jinhyoung.salary.budgetitem;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배분 항목 생성·조회·삭제(ITEM-01·ITEM-09, API명세 4장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로
 * 소유분만 다룬다. DELETE는 물리 삭제가 아닌 status=DELETED(soft delete, 규칙 5). 수정(PATCH)은 ITEM-07.
 */
@RestController
@RequestMapping("/api/v1/budget-items")
public class BudgetItemController {

    /** 이름·메모 길이 상한 — ERD 컬럼 길이 및 구현규칙 5장과 일치. */
    private static final int NAME_MAX = 50;

    private static final int MEMO_MAX = 500;

    /** 금액 범위(원) — 구현규칙 5장: 1 ≤ x ≤ 10억. */
    private static final long AMOUNT_MIN = 1;

    private static final long AMOUNT_MAX = 1_000_000_000;

    private final BudgetItemService budgetItemService;

    public BudgetItemController(BudgetItemService budgetItemService) {
        this.budgetItemService = budgetItemService;
    }

    @GetMapping
    public List<BudgetItemResponse> list(@AuthenticationPrincipal Long userId) {
        return budgetItemService.list(userId).stream()
                .map(BudgetItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BudgetItemResponse get(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        return BudgetItemResponse.from(budgetItemService.get(userId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetItemResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateRequest request) {
        BudgetItem item = budgetItemService.create(
                userId,
                request.category(),
                request.name(),
                request.amount(),
                request.accountId(),
                request.startDate(),
                request.endDate(),
                request.memo());
        return BudgetItemResponse.from(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        budgetItemService.delete(userId, id);
    }

    /** 만기일(endDate)은 선택(기한 없는 항목은 null). 있으면 시작일보다 뒤여야 한다(구현규칙 5장). */
    public record CreateRequest(
            @NotNull Category category,
            @NotBlank @Size(max = NAME_MAX) String name,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long amount,
            @NotNull Long accountId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = MEMO_MAX) String memo) {

        /** end_date > start_date 교차 검증(ITEM-02). 위반 시 400 VALIDATION_FAILED(핸들러가 코드만 반환). */
        @AssertTrue
        public boolean isEndDateAfterStartDate() {
            return endDate == null || startDate == null || endDate.isAfter(startDate);
        }
    }

    public record BudgetItemResponse(
            Long id,
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            String memo,
            int sortOrder) {
        static BudgetItemResponse from(BudgetItem item) {
            return new BudgetItemResponse(
                    item.getId(),
                    item.getCategory(),
                    item.getName(),
                    item.getAmount(),
                    item.getAccountId(),
                    item.getStartDate(),
                    item.getEndDate(),
                    item.getMemo(),
                    item.getSortOrder());
        }
    }
}
