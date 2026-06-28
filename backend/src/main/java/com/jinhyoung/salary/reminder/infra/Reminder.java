package com.jinhyoung.salary.reminder.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 사용자 정의 리마인더(ERD reminders, NOTI-06). 사용자가 메모와 주기를 정해 두면 일일 배치가 다음 알림일에
 * 한 번 발송하고 다음 주기로 미룬다. 엔티티는 infra에 둔다(아키텍처 v1.1) — 주기 산술은 순수 domain
 * ({@link com.jinhyoung.salary.reminder.domain.ReminderSchedule})이 맡는다.
 *
 * <p>이 서비스는 "리마인더 발송"만 한다 — 비범위(가계부)를 침범하지 않는다(규칙 1). 문구는 코드가 만들지 않고
 * label(사용자 메모)을 메시지 번들 인자로 넘겨 클라이언트/채널이 조립한다(규칙 7). soft delete는 status로(규칙 5).
 */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 사용자 메모 — 알림 본문 렌더 인자(문장은 번들/클라가 조립, 규칙 7). */
    @Column(nullable = false)
    private String label;

    /** 사용자 정의 주기(개월). 발송 후 다음 알림일을 이만큼 미룬다. 1 이상(smallint 컬럼이라 Short 매핑). */
    @Column(name = "interval_months", nullable = false)
    private Short intervalMonths;

    @Column(name = "next_remind_date", nullable = false)
    private LocalDate nextRemindDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderStatus status;

    protected Reminder() {
        // JPA
    }

    private Reminder(Long userId, String label, Short intervalMonths, LocalDate nextRemindDate) {
        this.userId = userId;
        this.label = label;
        this.intervalMonths = intervalMonths;
        this.nextRemindDate = nextRemindDate;
        this.status = ReminderStatus.ACTIVE;
    }

    /** 새 리마인더 생성(NOTI-06). status는 ACTIVE로 시작한다. */
    public static Reminder create(Long userId, String label, Short intervalMonths, LocalDate nextRemindDate) {
        return new Reminder(userId, label, intervalMonths, nextRemindDate);
    }

    /**
     * 리마인더 수정(NOTI-06) — 메모·주기·다음 알림일을 갱신한다(전체 교체). user_id·status는 불변이다
     * (상태 전이는 soft delete가 따로 수행).
     */
    public void update(String label, Short intervalMonths, LocalDate nextRemindDate) {
        this.label = label;
        this.intervalMonths = intervalMonths;
        this.nextRemindDate = nextRemindDate;
    }

    /** soft delete(NOTI-06) — 상태를 DELETED로 전환한다. 행은 잔존하며 물리 삭제는 회원 탈퇴 cascade뿐(규칙 5). */
    public void markDeleted() {
        this.status = ReminderStatus.DELETED;
    }

    /**
     * 발송 후 다음 알림일을 미룬다(NOTI-06). 다음 알림일은 순수
     * {@link com.jinhyoung.salary.reminder.domain.ReminderSchedule}가 계산하고 여기선 그 결과만 반영한다.
     */
    public void rescheduleTo(LocalDate nextRemindDate) {
        this.nextRemindDate = nextRemindDate;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public Short getIntervalMonths() {
        return intervalMonths;
    }

    public LocalDate getNextRemindDate() {
        return nextRemindDate;
    }

    public ReminderStatus getStatus() {
        return status;
    }
}
