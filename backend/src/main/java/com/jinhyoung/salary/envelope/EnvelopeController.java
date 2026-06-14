package com.jinhyoung.salary.envelope;

import com.jinhyoung.salary.envelope.infra.Envelope;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 봉투 생성·조회·수정·삭제(ENV-01, API명세 4장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로
 * 소유분만 다룬다. DELETE는 물리 삭제가 아닌 status=DELETED(soft delete, 규칙 5). 월 적립액 계산(ENV-02)·
 * 진행률/D-day(ENV-03)·지출 처리(ENV-04~05)는 별도 요구사항이라 이 컨트롤러 범위 밖이다.
 */
@RestController
@RequestMapping("/api/v1/envelopes")
public class EnvelopeController {

    /** 이름·메모 길이 상한 — ERD 컬럼 길이 및 구현규칙 5장과 일치. */
    private static final int NAME_MAX = 50;

    private static final int MEMO_MAX = 500;

    /** 금액 범위(원) — 구현규칙 5장: 1 ≤ x ≤ 10억. */
    private static final long AMOUNT_MIN = 1;

    private static final long AMOUNT_MAX = 1_000_000_000;

    /** 반복 주기(개월) 범위 — 1 이상(0개월 반복은 무의미), smallint 안쪽으로 상한. */
    private static final int CYCLE_MONTHS_MIN = 1;

    private static final int CYCLE_MONTHS_MAX = 1200;

    private final EnvelopeService envelopeService;

    public EnvelopeController(EnvelopeService envelopeService) {
        this.envelopeService = envelopeService;
    }

    @GetMapping
    public List<EnvelopeResponse> list(@AuthenticationPrincipal Long userId) {
        return envelopeService.list(userId).stream().map(EnvelopeResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EnvelopeResponse get(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        return EnvelopeResponse.from(envelopeService.get(userId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvelopeResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody EnvelopeRequest request) {
        return EnvelopeResponse.from(envelopeService.create(
                userId,
                request.accountId(),
                request.name(),
                request.targetAmount(),
                request.nextDueDate(),
                request.cycleMonthsAsShort(),
                request.memo()));
    }

    @PatchMapping("/{id}")
    public EnvelopeResponse update(
            @AuthenticationPrincipal Long userId, @PathVariable long id, @Valid @RequestBody EnvelopeRequest request) {
        return EnvelopeResponse.from(envelopeService.update(
                userId,
                id,
                request.accountId(),
                request.name(),
                request.targetAmount(),
                request.nextDueDate(),
                request.cycleMonthsAsShort(),
                request.memo()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        envelopeService.delete(userId, id);
    }

    /**
     * 봉투 생성·수정 요청(ENV-01). 생성과 수정이 동일한 편집 필드·검증을 쓴다(부분 갱신이 아닌 전체 교체 —
     * 봉투 폼이 전 필드를 다시 제출). {@code cycleMonths}는 선택(null=일회성), 있으면 1 이상이어야 한다.
     * {@code nextDueDate ≥ 오늘}은 KST Clock 판정이 필요해 서비스에서 검증한다(구현규칙 5장).
     */
    public record EnvelopeRequest(
            @NotNull Long accountId,
            @NotBlank @Size(max = NAME_MAX) String name,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long targetAmount,
            @NotNull LocalDate nextDueDate,
            @Min(CYCLE_MONTHS_MIN) @Max(CYCLE_MONTHS_MAX) Integer cycleMonths,
            @Size(max = MEMO_MAX) String memo) {

        /** smallint 컬럼 매핑(Short)으로 변환. null=일회성. */
        Short cycleMonthsAsShort() {
            return cycleMonths == null ? null : cycleMonths.shortValue();
        }
    }

    /**
     * 봉투 조회 응답. CRUD 필드(ENV-01)에 더해 조회 시점 파생값(ENV-03)을 싣는다: {@code progressPercent}(적립
     * 진행률 %, 내림), {@code dDay}(다음 지출일까지 일수, 음수=경과), {@code monthlyAmount}(이번 사이클 월 적립액).
     * 파생값은 컬럼이 아니라 계산값이라 서비스가 {@link EnvelopeService.EnvelopeView}로 조립해 넘긴다.
     */
    public record EnvelopeResponse(
            Long id,
            Long accountId,
            String name,
            long targetAmount,
            long savedAmount,
            LocalDate nextDueDate,
            Integer cycleMonths,
            String memo,
            int progressPercent,
            long dDay,
            long monthlyAmount) {
        static EnvelopeResponse from(EnvelopeService.EnvelopeView view) {
            Envelope envelope = view.envelope();
            Short cycleMonths = envelope.getCycleMonths();
            return new EnvelopeResponse(
                    envelope.getId(),
                    envelope.getAccountId(),
                    envelope.getName(),
                    envelope.getTargetAmount(),
                    envelope.getSavedAmount(),
                    envelope.getNextDueDate(),
                    cycleMonths == null ? null : cycleMonths.intValue(),
                    envelope.getMemo(),
                    view.progressPercent(),
                    view.dDay(),
                    view.monthlyAmount());
        }
    }
}
