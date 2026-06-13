package com.jinhyoung.salary.user.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 조회·저장. (provider, provider_id)는 ERD unique 제약 — 공급자별 계정 분리(AUTH-03).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
