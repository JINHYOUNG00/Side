package com.jinhyoung.salary.reminder;

import com.jinhyoung.salary.reminder.infra.Reminder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 정의 리마인더 생성·조회·수정·삭제(NOTI-06). 인증 필수 — principal=userId(JwtAuthenticationFilter)로
 * 소유분만 다룬다. DELETE는 물리 삭제가 아닌 status=DELETED(soft delete, 규칙 5). 분기 외화 점검은 계산형
 * 판정이라 별도 리소스가 없다 — 이 컨트롤러는 메모 기반 사용자 정의 리마인더만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    /** 메모 길이 상한 — ERD reminders.label 컬럼 길이와 일치. */
    private static final int LABEL_MAX = 100;

    /** 주기(개월) 범위 — 1 이상(0개월 반복은 무의미), 10년 안쪽으로 상한. */
    private static final int INTERVAL_MONTHS_MIN = 1;

    private static final int INTERVAL_MONTHS_MAX = 120;

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public List<ReminderResponse> list(@AuthenticationPrincipal Long userId) {
        return reminderService.list(userId).stream().map(ReminderResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody ReminderRequest request) {
        return ReminderResponse.from(reminderService.create(
                userId, request.label(), request.intervalMonthsAsShort(), request.nextRemindDate()));
    }

    @PatchMapping("/{id}")
    public ReminderResponse update(
            @AuthenticationPrincipal Long userId, @PathVariable long id, @Valid @RequestBody ReminderRequest request) {
        return ReminderResponse.from(reminderService.update(
                userId, id, request.label(), request.intervalMonthsAsShort(), request.nextRemindDate()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        reminderService.delete(userId, id);
    }

    /**
     * 리마인더 생성·수정 요청(NOTI-06). 생성과 수정이 동일한 편집 필드·검증을 쓴다(부분 갱신이 아닌 전체 교체).
     * {@code nextRemindDate ≥ 오늘}은 KST Clock 판정이 필요해 서비스에서 검증한다.
     */
    public record ReminderRequest(
            @NotBlank @Size(max = LABEL_MAX) String label,
            @Min(INTERVAL_MONTHS_MIN) @Max(INTERVAL_MONTHS_MAX) int intervalMonths,
            @NotNull LocalDate nextRemindDate) {

        /** smallint 컬럼 매핑(Short)으로 변환. */
        Short intervalMonthsAsShort() {
            return (short) intervalMonths;
        }
    }

    /** 리마인더 조회 응답(NOTI-06). 저장 필드만 싣는다 — 다음 알림일은 발송 후 배치가 다음 주기로 이월한다. */
    public record ReminderResponse(Long id, String label, int intervalMonths, LocalDate nextRemindDate) {
        static ReminderResponse from(Reminder reminder) {
            return new ReminderResponse(
                    reminder.getId(),
                    reminder.getLabel(),
                    reminder.getIntervalMonths().intValue(),
                    reminder.getNextRemindDate());
        }
    }
}
