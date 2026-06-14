package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * 현재 사이클 + 통장별 체크리스트 응답(CYCLE-06, API명세 5장 {@code GET /cycles/current}).
 * 사이클 헤더(경계·확인 실수령액)에 plan_lines를 이체 대상 통장({@code account_id})별로 묶은 체크리스트와
 * 진행도를 더한다. 문장은 담지 않는다 — LIVING 등 머신 토큰은 클라이언트가 i18n으로 렌더(규칙 7).
 *
 * @param checklist 통장별 그룹. 라인 입력순(id asc)의 통장 첫 등장 순서를 보존한다.
 * @param progress done = 처리된(PENDING이 아닌) 라인 수, total = 전체 라인 수
 */
public record ChecklistResponse(
        Long id,
        String label,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        long income,
        boolean incomeConfirmed,
        List<AccountGroup> checklist,
        Progress progress) {

    /** 한 통장으로 묶인 이체 라인 묶음. total = 그룹 내 planned_amount 합. */
    public record AccountGroup(Long accountId, String accountName, long total, List<Line> lines) {}

    /** 체크리스트 한 줄 — PATCH /plan-lines/{id} 응답으로도 단건 반환된다. */
    public record Line(Long id, String name, long plannedAmount, PlanLineStatus status) {}

    /** 진행도 — done(처리됨)/total(전체). done == total이면 처리할 라인이 남지 않은 상태. */
    public record Progress(int done, int total) {}
}
