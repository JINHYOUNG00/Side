package com.jinhyoung.salary.checkin;

import com.jinhyoung.salary.checkin.infra.CheckIn;
import com.jinhyoung.salary.checkin.infra.CheckInRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월말 체크인 기록 유스케이스(RPT-01). 사이클 종료 전일에 생활비 통장 잔액과 추가 투입액을 받아 계획 대비 실제를
 * 기록한다 — 소유권 검증을 이 한 곳(+리포지토리 쿼리)으로 모아 컨트롤러가 우회할 수 없게 한다(아키텍처 8장).
 *
 * <p>대상 사이클은 호출 사용자의 것이어야 하며(미소유·부재는 NOT_FOUND로 존재 비노출), 사이클당 1건만 허용한다
 * (이미 있으면 409 {@code CHECK_IN_ALREADY_EXISTS}, ERD 3장 멱등 제약). 초과액 계산·저장은 엔티티가 순수
 * {@link com.jinhyoung.salary.checkin.domain.CheckInReconciliation}에 위임한다.
 */
@Service
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CycleRepository cycleRepository;

    public CheckInService(CheckInRepository checkInRepository, CycleRepository cycleRepository) {
        this.checkInRepository = checkInRepository;
        this.cycleRepository = cycleRepository;
    }

    /**
     * 체크인 기록(RPT-01). 호출 사용자의 사이클에만 기록하며(미소유·부재는 NOT_FOUND), 사이클당 1건만 허용한다
     * (중복은 409). {@code toppedUp}는 선택 입력으로, 생략 시 0으로 다룬다(ERD default 0). 초과액은 엔티티가
     * 계산해 저장한다.
     */
    @Transactional
    public CheckIn record(long userId, long cycleId, long livingRemaining, long toppedUp, String note) {
        Cycle cycle = cycleRepository
                .findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "cycle", "id", cycleId)));
        if (checkInRepository.existsByCycleId(cycle.getId())) {
            throw new ApiException(ErrorCode.CHECK_IN_ALREADY_EXISTS, Map.of("cycleId", cycle.getId()));
        }
        return checkInRepository.save(CheckIn.create(cycle.getId(), livingRemaining, toppedUp, note));
    }
}
