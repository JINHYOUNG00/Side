package com.jinhyoung.salary.cycle.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 공휴일 캐시(ERD holidays, CYCLE-01). 전역 참조 테이블 — 사용자와 무관하다. 공공데이터포털 특일 API에서 연 1회
 * 수집해 적재하며, {@code holiday_date}에 unique 제약이 있어 재수집이 멱등하다(규칙 8).
 *
 * <p>엔티티는 infra에 둔다(아키텍처 v1.1) — domain({@link com.jinhyoung.salary.cycle.domain.PaydayResolver})은
 * 이 테이블을 직접 보지 않고, 호출자가 주입한 {@code Set<LocalDate>}만 본다.
 */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    /** 수집 기준 연도 — 연 1회 캐싱 여부 판정에 쓴다(CYCLE-01 "연 1회"·차년도 미제공 폴백). */
    @Column(name = "source_year", nullable = false)
    private short sourceYear;

    protected Holiday() {}

    private Holiday(LocalDate holidayDate, String name, short sourceYear) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.sourceYear = sourceYear;
    }

    public static Holiday of(LocalDate holidayDate, String name, int sourceYear) {
        return new Holiday(holidayDate, name, (short) sourceYear);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }

    public short getSourceYear() {
        return sourceYear;
    }
}
