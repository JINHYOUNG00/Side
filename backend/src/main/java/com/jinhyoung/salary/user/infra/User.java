package com.jinhyoung.salary.user.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자(ERD users). 엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만.
 *
 * <p>OAuth 첫 로그인 시 식별 정보(provider/provider_id/email/nickname)만 확정되고,
 * base_income·payday·payday_adjustment는 NOT NULL이지만 온보딩(SET) 전이라 값이 없다.
 * 따라서 신규 사용자는 플레이스홀더로 채워 행을 만들고, isNewUser=true로 온보딩을 유도한다.
 * 이 값들은 온보딩에서 사용자가 실제 값으로 덮어쓴다.
 */
@Entity
@Table(name = "users")
public class User {

    /** 온보딩 전 플레이스홀더 — 실수령액 미입력 상태. */
    private static final long UNSET_INCOME = 0L;

    /** 온보딩 전 플레이스홀더 월급일(1~31 제약 충족용). */
    private static final short UNSET_PAYDAY = 1;

    /** 월급일 조정 없음(ERD payday_adjustment: PREV_BUSINESS_DAY/NEXT_BUSINESS_DAY/NONE). */
    private static final String ADJUSTMENT_NONE = "NONE";

    private static final String DEFAULT_LOCALE = "ko";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "base_income", nullable = false)
    private long baseIncome;

    @Column(nullable = false)
    private short payday;

    @Column(name = "payday_adjustment", nullable = false)
    private String paydayAdjustment;

    @Column(name = "include_investment_in_savings_rate", nullable = false)
    private boolean includeInvestmentInSavingsRate;

    @Column(nullable = false)
    private String locale;

    protected User() {
        // JPA
    }

    private User(String provider, String providerId, String email, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.baseIncome = UNSET_INCOME;
        this.payday = UNSET_PAYDAY;
        this.paydayAdjustment = ADJUSTMENT_NONE;
        this.includeInvestmentInSavingsRate = true;
        this.locale = DEFAULT_LOCALE;
    }

    /**
     * OAuth 첫 로그인으로 신규 사용자 생성. 온보딩 전이라 설정값은 플레이스홀더.
     * provider/providerId는 어댑터가 정규화한 값, nickname은 호출 측에서 폴백 적용 후 전달(NOT NULL).
     */
    public static User createFromOAuth(String provider, String providerId, String email, String nickname) {
        return new User(provider, providerId, email, nickname);
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}
