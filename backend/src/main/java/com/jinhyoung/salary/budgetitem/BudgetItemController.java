package com.jinhyoung.salary.budgetitem;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.domain.FxFrequency;
import com.jinhyoung.salary.budgetitem.domain.FxRecommendationCalculator;
import com.jinhyoung.salary.budgetitem.domain.FxRecommendationInput;
import com.jinhyoung.salary.budgetitem.domain.FxRecommendationResult;
import com.jinhyoung.salary.budgetitem.domain.MaturityArchiveStats;
import com.jinhyoung.salary.budgetitem.domain.MaturityCalculator;
import com.jinhyoung.salary.budgetitem.domain.MaturityInput;
import com.jinhyoung.salary.budgetitem.domain.MaturityResult;
import com.jinhyoung.salary.budgetitem.domain.TaxType;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.common.PolicyProperties;
import com.jinhyoung.salary.cycle.CycleSnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배분 항목 생성·조회·삭제(ITEM-01·ITEM-09, API명세 4장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로
 * 소유분만 다룬다. DELETE는 물리 삭제가 아닌 status=DELETED(soft delete, 규칙 5). 수정(PATCH)은 ITEM-07.
 * 보관함 조회와 실수령액 기록(ITEM-08)은 {@code GET /archive}·{@code PATCH /{id}/maturity}로 다룬다.
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

    /** 연이율(%) 상한 — interest_rate numeric(5,2)와 일치(정수 3자리·소수 2자리), 0 ≤ x ≤ 100. */
    private static final String RATE_MIN = "0.0";

    private static final String RATE_MAX = "100.00";

    /** 납입 개월 수 상한(만기 미리보기) — 50년치. */
    private static final int MONTHS_MAX = 600;

    /** 외화 도우미(ITEM-04) 입력 상한 — 일 외화 금액·기준 환율의 합리적 범위(서버 방어). */
    private static final String FX_UNIT_MAX = "1000000";

    private static final String FX_RATE_MAX = "100000";

    private final BudgetItemService budgetItemService;
    private final CycleSnapshotService cycleSnapshotService;
    private final PolicyProperties policyProperties;

    public BudgetItemController(
            BudgetItemService budgetItemService,
            CycleSnapshotService cycleSnapshotService,
            PolicyProperties policyProperties) {
        this.budgetItemService = budgetItemService;
        this.cycleSnapshotService = cycleSnapshotService;
        this.policyProperties = policyProperties;
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

    /**
     * 보관함 조회(ITEM-08, SCR-08). 만기·중도해지로 보관(ARCHIVED)된 항목 목록과 이력 통계(보관 건수·실수령액
     * 기록 건수·만기 수령 누적액)를 함께 내린다. 항목에는 예상(ITEM-05/06)·실제 만기금액을 실어 "예상 vs 실제"를
     * 표시할 수 있게 한다(예상은 Phase 5 전까지 null).
     */
    @GetMapping("/archive")
    public ArchiveResponse archive(@AuthenticationPrincipal Long userId) {
        return ArchiveResponse.from(budgetItemService.listArchived(userId));
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
                request.interestRate(),
                request.taxType(),
                request.expectedMaturityAmount(),
                request.memo());
        return BudgetItemResponse.from(item);
    }

    /**
     * 항목 수정(ITEM-07). 수정은 budget_items 원본만 바꾸므로 현재 사이클 스냅샷(plan_lines)은 불변이고
     * 수정값은 다음 사이클 생성 시 반영된다(기본 동작 = "다음 사이클부터 적용"). {@code applyToCurrentCycle=true}일
     * 때만 현재 사이클의 미완료 라인을 재계산한다(완료 라인 보존 — 구현규칙 4장 재생성 절차).
     */
    @PatchMapping("/{id}")
    public BudgetItemResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean applyToCurrentCycle,
            @Valid @RequestBody UpdateRequest request) {
        BudgetItem item = budgetItemService.update(
                userId,
                id,
                request.category(),
                request.name(),
                request.amount(),
                request.accountId(),
                request.startDate(),
                request.endDate(),
                request.interestRate(),
                request.taxType(),
                request.expectedMaturityAmount(),
                request.memo());
        if (applyToCurrentCycle) {
            cycleSnapshotService.regenerateCurrentCycle(userId);
        }
        return BudgetItemResponse.from(item);
    }

    /**
     * 실수령액 기록(ITEM-08). 만기·중도해지 시 실제로 받은 금액을 기록한다. ACTIVE 항목이면 중도해지로 보아
     * ARCHIVED로 전환하고, 이미 ARCHIVED면 만기 수령액을 기록(정정)한다. DELETED·미소유·부재는 NOT_FOUND.
     */
    @PatchMapping("/{id}/maturity")
    public ArchivedItemResponse recordMaturity(
            @AuthenticationPrincipal Long userId,
            @PathVariable long id,
            @Valid @RequestBody RecordMaturityRequest request) {
        return ArchivedItemResponse.from(budgetItemService.recordMaturityActual(userId, id, request.actualAmount()));
    }

    /**
     * 적금 만기금액 미리보기(ITEM-05, API명세 4장) — 저장 없는 순수 계산이다. 월 납입액·개월 수·연이율·세금유형으로
     * 단리 공식(구현규칙 1장: 이자 반올림 → 세금 반올림)을 적용해 원금·이자·세금·만기 실수령액 분해를 돌려준다.
     * 결과는 "예상치"이며 은행 실수령과 수원 단위 차이가 날 수 있다. 인증은 필요하나 사용자 데이터는 읽지 않는다.
     */
    @PostMapping("/preview-maturity")
    public MaturityPreviewResponse previewMaturity(@Valid @RequestBody PreviewMaturityRequest request) {
        MaturityResult result = MaturityCalculator.calculate(new MaturityInput(
                request.monthlyAmount(), request.interestRate(), request.months(), request.taxType()));
        return new MaturityPreviewResponse(result.principal(), result.interest(), result.tax(), result.total());
    }

    /**
     * 외화 적립 도우미(ITEM-04, API명세 4장) — 저장 없는 순수 계산이다. 일/회 외화 금액·빈도(매일/영업일)·기준
     * 환율로 버퍼(app.policy.fx-buffer-rate, 구현규칙 6장)를 포함한 권장 월 이체액(원, 1,000원 단위 올림)을
     * 돌려준다. 실시간 환율 연동·일별 추적은 하지 않으며(요구사항정의서 ITEM-04) 저장은 원화 월액으로만 한다 —
     * 화면은 이 권장액을 항목 금액 입력에 채운다. 인증은 필요하나 사용자 데이터는 읽지 않는다.
     */
    @PostMapping("/preview-fx")
    public FxPreviewResponse previewFx(@Valid @RequestBody PreviewFxRequest request) {
        FxRecommendationResult result = FxRecommendationCalculator.calculate(new FxRecommendationInput(
                request.unitAmount(), request.frequency(), request.fxRate(), policyProperties.fxBufferRate()));
        return new FxPreviewResponse(result.recommendedMonthlyKrw(), result.bufferRate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        budgetItemService.delete(userId, id);
    }

    /**
     * 항목 생성 요청. 만기일(endDate)은 선택(기한 없는 항목은 null). 있으면 시작일보다 뒤여야 한다(구현규칙 5장).
     * 저축 조건부 필드(interestRate·taxType·expectedMaturityAmount, ITEM-05/06)는 전부 선택 — 비저축 항목이면
     * 보내지 않는다. 이율·세금이 있으면 만기금액을 공식 계산하고, expectedMaturityAmount(수동값)가 있으면 공식
     * 대신 그 값을 쓴다(특수 상품, ERD). 어느 쪽도 강제하지 않으며 표시 시점에 해석한다.
     */
    public record CreateRequest(
            @NotNull Category category,
            @NotBlank @Size(max = NAME_MAX) String name,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long amount,
            @NotNull Long accountId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @DecimalMin(RATE_MIN) @DecimalMax(RATE_MAX) @Digits(integer = 3, fraction = 2) BigDecimal interestRate,
            TaxType taxType,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) Long expectedMaturityAmount,
            @Size(max = MEMO_MAX) String memo) {

        /** end_date > start_date 교차 검증(ITEM-02). 위반 시 400 VALIDATION_FAILED(핸들러가 코드만 반환). */
        @AssertTrue
        public boolean isEndDateAfterStartDate() {
            return endDate == null || startDate == null || endDate.isAfter(startDate);
        }
    }

    /**
     * 항목 수정 요청(ITEM-07). 생성과 동일한 편집 가능 필드·검증을 적용한다(부분 갱신이 아닌 전체 교체 — 항목
     * 폼이 전 필드를 다시 제출). 적용 시점({@code applyToCurrentCycle})은 본문이 아니라 쿼리 파라미터로 받는다.
     */
    public record UpdateRequest(
            @NotNull Category category,
            @NotBlank @Size(max = NAME_MAX) String name,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long amount,
            @NotNull Long accountId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @DecimalMin(RATE_MIN) @DecimalMax(RATE_MAX) @Digits(integer = 3, fraction = 2) BigDecimal interestRate,
            TaxType taxType,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) Long expectedMaturityAmount,
            @Size(max = MEMO_MAX) String memo) {

        /** end_date > start_date 교차 검증(ITEM-02). 위반 시 400 VALIDATION_FAILED(핸들러가 코드만 반환). */
        @AssertTrue
        public boolean isEndDateAfterStartDate() {
            return endDate == null || startDate == null || endDate.isAfter(startDate);
        }
    }

    /**
     * 항목 응답. 저축 조건부 필드(interestRate·taxType·expectedMaturityAmount, ITEM-05/06)를 함께 실어 항목 폼이
     * 수정 시 프리필·재미리보기할 수 있게 한다. 여기서 expectedMaturityAmount는 <b>수동 입력 원본값</b>이다(폼의
     * 특수 상품 입력란 프리필용) — 공식 계산값과 합친 표시용 해석값은 보관함(ITEM-08, "예상 vs 실제")에서 내린다.
     */
    public record BudgetItemResponse(
            Long id,
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
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
                    item.getInterestRate(),
                    item.getTaxType(),
                    item.getExpectedMaturityAmount(),
                    item.getMemo(),
                    item.getSortOrder());
        }
    }

    /** 실수령액 기록 요청(ITEM-08). 금액은 long 원 단위, 1 ≤ x ≤ 10억(구현규칙 5장). */
    public record RecordMaturityRequest(@Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long actualAmount) {}

    /**
     * 만기금액 미리보기 요청(ITEM-05). 월 납입액·납입 개월 수·연이율(%)·세금유형 — 전부 필수. 이율은 numeric(5,2)
     * 범위(0~100, 소수 2자리), 개월 수는 1~600(50년).
     */
    public record PreviewMaturityRequest(
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long monthlyAmount,
            @Min(1) @Max(MONTHS_MAX) int months,
            @NotNull @DecimalMin(RATE_MIN) @DecimalMax(RATE_MAX) @Digits(integer = 3, fraction = 2)
                    BigDecimal interestRate,
            @NotNull TaxType taxType) {}

    /** 만기금액 미리보기 응답(ITEM-05) — 원금·세전 이자·이자과세·만기 실수령액. 전부 원 단위 long. 표시는 "예상치". */
    public record MaturityPreviewResponse(long principal, long interest, long tax, long total) {}

    /**
     * 외화 도우미 미리보기 요청(ITEM-04). 통화(currency)는 표시·보존용 메타라 계산에 쓰지 않으나 입력으로 받는다
     * (요구사항정의서 "통화" 입력). unitAmount·fxRate는 외화 금액·환율이라 long 원 단위 규칙(규칙 2) 대상이 아니며
     * 소수가 가능해 BigDecimal로 받는다 — 0 초과·합리적 상한·소수 자릿수 제한으로 방어한다.
     */
    public record PreviewFxRequest(
            @NotBlank @Size(max = 10) String currency,
            @NotNull
                    @DecimalMin(value = "0", inclusive = false)
                    @DecimalMax(FX_UNIT_MAX)
                    @Digits(integer = 7, fraction = 4)
                    BigDecimal unitAmount,
            @NotNull FxFrequency frequency,
            @NotNull
                    @DecimalMin(value = "0", inclusive = false)
                    @DecimalMax(FX_RATE_MAX)
                    @Digits(integer = 5, fraction = 4)
                    BigDecimal fxRate) {}

    /** 외화 도우미 미리보기 응답(ITEM-04) — 권장 월 이체액(원, 1,000원 단위 올림) + 적용 버퍼율("버퍼 N% 포함" 고지용). */
    public record FxPreviewResponse(long recommendedMonthlyKrw, BigDecimal bufferRate) {}

    /**
     * 보관함 항목(ITEM-08, SCR-08). 만기일·예상/실제 만기금액을 함께 실어 "예상 vs 실제"를 표시한다. 여기서
     * expectedMaturityAmount는 표시용 <b>해석값</b>이다(ITEM-05/06) — 수동 입력값이 있으면 그 값, 없으면 저축
     * 항목 단리 공식으로 계산하며(이율·세금·만기일이 없으면 null), maturityActualAmount는 미기록 시 null.
     */
    public record ArchivedItemResponse(
            Long id,
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            Long expectedMaturityAmount,
            Long maturityActualAmount,
            String memo,
            int sortOrder) {
        static ArchivedItemResponse from(BudgetItem item) {
            return new ArchivedItemResponse(
                    item.getId(),
                    item.getCategory(),
                    item.getName(),
                    item.getAmount(),
                    item.getAccountId(),
                    item.getStartDate(),
                    item.getEndDate(),
                    item.resolveExpectedMaturityAmount(),
                    item.getMaturityActualAmount(),
                    item.getMemo(),
                    item.getSortOrder());
        }
    }

    /**
     * 보관함 응답(ITEM-08) — 보관 항목 목록 + 이력 통계. 통계는 순수 클래스 {@link MaturityArchiveStats}가
     * 실수령액 목록으로부터 집계한다(누적 합산, 미기록 제외).
     */
    public record ArchiveResponse(List<ArchivedItemResponse> items, MaturityArchiveStats stats) {
        static ArchiveResponse from(List<BudgetItem> archived) {
            List<ArchivedItemResponse> items =
                    archived.stream().map(ArchivedItemResponse::from).toList();
            MaturityArchiveStats stats = MaturityArchiveStats.from(
                    archived.stream().map(BudgetItem::getMaturityActualAmount).toList());
            return new ArchiveResponse(items, stats);
        }
    }
}
