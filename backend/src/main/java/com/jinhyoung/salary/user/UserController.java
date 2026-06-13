package com.jinhyoung.salary.user;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필·기본 정보 설정(SET-01, API명세 2장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인만 다룬다.
 *
 * <p>{@code GET /me}는 프로필+설정을 조회하고, {@code PATCH /me}는 온보딩 기본 정보(실수령액·월급일·조정 규칙·생활비 통장)를
 * 등록·수정한다. 투자 포함 토글(SET-02)·언어(SET-03)는 각 Phase에서 쓰기를 추가하며, 지금은 응답에 현재값만 노출한다.
 */
@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    /** 실수령액 상한(구현규칙 5장 금액 규칙: 1 ~ 10억). */
    private static final long INCOME_MAX = 1_000_000_000L;

    /** 월급일 범위(구현규칙 5장: 1~31. 해당 월에 없으면 말일 간주). */
    private static final int PAYDAY_MIN = 1;

    private static final int PAYDAY_MAX = 31;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return MeResponse.from(userService.getMe(userId));
    }

    @PatchMapping
    public MeResponse update(@AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateRequest request) {
        User user = userService.updateSettings(
                userId,
                request.baseIncome(),
                (short) (int) request.payday(),
                request.paydayAdjustment(),
                request.livingAccountId());
        return MeResponse.from(user);
    }

    public record UpdateRequest(
            @NotNull @Min(1) @Max(INCOME_MAX) Long baseIncome,
            @NotNull @Min(PAYDAY_MIN) @Max(PAYDAY_MAX) Integer payday,
            @NotNull PaydayAdjustment paydayAdjustment,
            Long livingAccountId) {}

    public record MeResponse(
            Long id,
            String email,
            String nickname,
            long baseIncome,
            short payday,
            PaydayAdjustment paydayAdjustment,
            boolean includeInvestmentInSavingsRate,
            String locale,
            Long livingAccountId) {
        static MeResponse from(User user) {
            return new MeResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getBaseIncome(),
                    user.getPayday(),
                    user.getPaydayAdjustment(),
                    user.isIncludeInvestmentInSavingsRate(),
                    user.getLocale(),
                    user.getLivingAccountId());
        }
    }
}
