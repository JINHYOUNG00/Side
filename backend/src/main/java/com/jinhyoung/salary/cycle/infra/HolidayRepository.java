package com.jinhyoung.salary.cycle.infra;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 공휴일 캐시 조회·적재(CYCLE-01). */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** 실지급일 산출에 필요한 구간의 공휴일만 읽는다(경계 양끝 포함). */
    List<Holiday> findByHolidayDateBetween(LocalDate from, LocalDate to);

    /** 해당 연도가 이미 캐싱돼 있는지 — "연 1회" 수집·재수집 스킵 판정(규칙 8 멱등). */
    boolean existsBySourceYear(short sourceYear);

    /** 적재 시 unique(holiday_date) 충돌을 피하기 위한 기존 날짜 조회. */
    boolean existsByHolidayDate(LocalDate holidayDate);
}
