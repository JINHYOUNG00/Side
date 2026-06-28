package com.jinhyoung.salary.user;

import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필·기본 정보 설정 유스케이스(SET-01). 모든 동작은 인증된 본인(userId)에 한정한다.
 *
 * <p>생활비 통장 지정 시 그 통장이 호출자의 활성 통장인지 검증한다 — 통장 CRUD와 동일하게
 * 미소유·삭제·부재는 모두 NOT_FOUND로 떨어뜨려 타인 통장 존재 여부를 노출하지 않는다(API명세 8장).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public User getMe(long userId) {
        return existingUser(userId);
    }

    /**
     * 기본 정보 등록·수정(SET-01). baseIncome·payday·paydayAdjustment는 필수,
     * livingAccountId는 선택(null이면 생활비 통장 미지정). 값 범위 검증은 컨트롤러 bean validation이 담당하고,
     * 여기서는 생활비 통장 소유권만 추가로 검증한다.
     *
     * <p>locale(SET-03)은 선택 — null이면 기존 언어를 보존하고, 값이 있으면(ko/en, 컨트롤러 검증) 반영한다.
     * 알림 발송은 user.locale을 그대로 읽어 언어를 고르므로(EmailNotificationSender) 별도 연동이 필요 없다.
     *
     * <p>includeInvestmentInSavingsRate(SET-02)도 선택 — null이면 기존 설정을 보존하고, 값이 있으면 반영한다.
     * 저축률 산정은 폭포·리포트가 user 설정을 그대로 읽으므로(SavingsRate) 별도 연동이 필요 없다.
     */
    @Transactional
    public User updateSettings(
            long userId,
            long baseIncome,
            short payday,
            PaydayAdjustment paydayAdjustment,
            Long livingAccountId,
            String locale,
            Boolean includeInvestmentInSavingsRate) {
        User user = existingUser(userId);
        if (livingAccountId != null) {
            requireOwnedActiveAccount(userId, livingAccountId);
        }
        user.updateSettings(baseIncome, payday, paydayAdjustment, livingAccountId);
        if (locale != null) {
            user.updateLocale(locale);
        }
        if (includeInvestmentInSavingsRate != null) {
            user.updateInvestmentInclusion(includeInvestmentInSavingsRate);
        }
        return user;
    }

    /**
     * 회원 탈퇴(AUTH-04): 인증된 본인 데이터를 전부 물리 삭제한다. soft delete가 아닌 유일한 물리 삭제 경로(규칙 5).
     * users 행 삭제만 수행하면 하위 도메인(accounts·budget_items·envelopes·envelope_transactions·cycles·
     * plan_lines·check_ins·suggestions·notification_logs·reminders)은 FK {@code ON DELETE CASCADE}로 함께
     * 제거된다(ERD 1·3장, V1/V2 마이그레이션). 멱등 — 이미 삭제된 사용자(토큰만 유효)면 아무것도 하지 않고 정상 종료한다.
     */
    @Transactional
    public void delete(long userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        }
    }

    private User existingUser(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "user", "id", userId)));
    }

    /** 생활비 통장이 호출자의 활성 통장인지 검증 — 통장 도메인의 소유권 관문을 재사용. */
    private void requireOwnedActiveAccount(long userId, long accountId) {
        accountRepository
                .findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "account", "id", accountId)));
    }
}
