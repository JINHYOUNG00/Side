package com.jinhyoung.salary.user.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 조회·저장. (provider, provider_id)는 ERD unique 제약 — 공급자별 계정 분리(AUTH-03).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * 온보딩을 마친 사용자(실수령액 등록됨). 플레이스홀더(base_income=0)는 월급일이 임의값이라 지급일 알림 대상이
     * 아니다(NOTI-01).
     */
    List<User> findByBaseIncomeGreaterThan(long threshold);
}
