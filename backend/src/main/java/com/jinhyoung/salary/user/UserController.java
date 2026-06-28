package com.jinhyoung.salary.user;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필·기본 정보 설정(SET-01, API명세 2장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인만 다룬다.
 *
 * <p>{@code GET /me}는 프로필+설정을 조회하고, {@code PATCH /me}는 온보딩 기본 정보(실수령액·월급일·조정 규칙·생활비 통장)를
 * 등록·수정한다. 언어(SET-03, locale ko/en)와 투자 포함 토글(SET-02, includeInvestmentInSavingsRate)도 같은
 * PATCH로 갱신하는데 — 온보딩 필수값과 달리 선택 필드라 값이 있을 때만 반영하고 없으면 기존 값을 보존한다.
 */
@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    /** 실수령액 상한(구현규칙 5장 금액 규칙: 1 ~ 10억). */
    private static final long INCOME_MAX = 1_000_000_000L;

    /** 월급일 범위(구현규칙 5장: 1~31. 해당 월에 없으면 말일 간주). */
    private static final int PAYDAY_MIN = 1;

    private static final int PAYDAY_MAX = 31;

    /** 지원 언어(SET-03, ERD locale ko/en). 그 외 값은 VALIDATION_FAILED. */
    private static final String LOCALE_PATTERN = "ko|en";

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
                request.livingAccountId(),
                request.locale(),
                request.includeInvestmentInSavingsRate());
        return MeResponse.from(user);
    }

    /**
     * 회원 탈퇴(AUTH-04, API명세: DELETE /me). 인증된 본인만 호출 가능(principal=userId)하므로 타인 데이터는
     * 구조적으로 침범할 수 없다. 전체 데이터를 영구 삭제하고 본문 없이 204를 반환한다. 멱등 — 이미 삭제된 경우에도 204.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId) {
        userService.delete(userId);
    }

    public record UpdateRequest(
            @NotNull @Min(1) @Max(INCOME_MAX) Long baseIncome,
            @NotNull @Min(PAYDAY_MIN) @Max(PAYDAY_MAX) Integer payday,
            @NotNull PaydayAdjustment paydayAdjustment,
            Long livingAccountId,
            @Pattern(regexp = LOCALE_PATTERN) String locale,
            Boolean includeInvestmentInSavingsRate) {}

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
