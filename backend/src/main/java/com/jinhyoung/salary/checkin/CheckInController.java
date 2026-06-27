package com.jinhyoung.salary.checkin;

import com.jinhyoung.salary.checkin.infra.CheckIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월말 체크인 기록(RPT-01, API명세 6장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 소유 사이클만
 * 다룬다. 사이클당 1건이라({@code unique(cycle_id)}) 재요청은 409 {@code CHECK_IN_ALREADY_EXISTS}.
 * 추이 리포트 조회(GET /reports/*)는 RPT-02 소관으로 여기서 다루지 않는다.
 */
@RestController
@RequestMapping("/api/v1/check-ins")
public class CheckInController {

    /** 금액 범위(원) — 잔액·투입액은 0(전액 소진·미투입) 허용, 상한은 구현규칙 5장의 10억. */
    private static final long AMOUNT_MIN = 0;

    private static final long AMOUNT_MAX = 1_000_000_000;

    private static final int NOTE_MAX = 500;

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CheckInRequest request) {
        CheckIn checkIn = checkInService.record(
                userId, request.cycleId(), request.livingRemaining(), request.toppedUpOrZero(), request.note());
        return CheckInResponse.from(checkIn);
    }

    /**
     * 체크인 기록 요청(RPT-01). {@code cycleId}는 대상 사이클, {@code livingRemaining}은 생활비 통장 잔액(필수).
     * {@code toppedUp}은 사이클 중 추가 투입액으로 선택이며 생략 시 0으로 다룬다(ERD default 0). 잔액·투입액은
     * 0~10억(구현규칙 5장 상한, 0은 전액 소진·미투입 허용).
     */
    public record CheckInRequest(
            @NotNull Long cycleId,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) long livingRemaining,
            @Min(AMOUNT_MIN) @Max(AMOUNT_MAX) Long toppedUp,
            @Size(max = NOTE_MAX) String note) {

        /** 선택 입력 — 생략(null)이면 0(ERD default). */
        long toppedUpOrZero() {
            return toppedUp == null ? 0L : toppedUp;
        }
    }

    /**
     * 체크인 조회 응답. 입력값(잔액·투입액)에 더해 계산·저장된 {@code overspend}(양수=초과, 음수=잉여)를 싣는다 —
     * 클라이언트가 초과/잉여 여부를 부호로 판정해 문구를 조립한다(규칙 7).
     */
    public record CheckInResponse(
            Long id, Long cycleId, long livingRemaining, long toppedUp, long overspend, String note) {
        static CheckInResponse from(CheckIn checkIn) {
            return new CheckInResponse(
                    checkIn.getId(),
                    checkIn.getCycleId(),
                    checkIn.getLivingRemaining(),
                    checkIn.getToppedUp(),
                    checkIn.getOverspend(),
                    checkIn.getNote());
        }
    }
}
