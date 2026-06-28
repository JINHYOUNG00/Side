package com.jinhyoung.salary.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import com.jinhyoung.salary.suggestion.domain.SuggestionType;
import com.jinhyoung.salary.suggestion.infra.Suggestion;
import com.jinhyoung.salary.suggestion.infra.SuggestionRepository;
import com.jinhyoung.salary.suggestion.infra.SuggestionStatus;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 여윳돈/부족 제안 배분 적용(CYCLE-05) 통합 테스트. 실 PostgreSQL(Testcontainers)에 사이클·plan_lines·제안을 박은 뒤
 * {@code POST /suggestions/{id}/allocate}로 이번 사이클 계획을 조정하고, 대상·LIVING 금액 반영·제안 APPLIED 전이와
 * 게이트(차액 초과 400·DONE 라인 400·LIVING 직접 대상 400·타인 404·이미 해소 409)를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WindfallAllocationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    PlanLineRepository planLineRepository;

    @Autowired
    SuggestionRepository suggestionRepository;

    @BeforeEach
    void clear() {
        suggestionRepository.deleteAll();
        planLineRepository.deleteAll();
        cycleRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newCycle(long userId) {
        CycleDefinition definition =
                new CycleDefinition(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 24), "2026-06");
        return cycleRepository.save(Cycle.create(userId, definition, 2_473_110)).getId();
    }

    private long livingLine(long cycleId, long planned) {
        return planLineRepository
                .save(PlanLine.living(cycleId, null, "생활비통장", planned))
                .getId();
    }

    private long itemLine(long cycleId, long planned) {
        return planLineRepository
                .save(PlanLine.item(cycleId, null, null, "청년도약계좌", "SAVING", "국민", planned))
                .getId();
    }

    private long suggestion(long userId, long cycleId, SuggestionType type, long difference) {
        return suggestionRepository
                .save(Suggestion.create(userId, type, Map.of("cycleId", cycleId, "difference", difference)))
                .getId();
    }

    private ResultActions allocate(long userId, long suggestionId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/suggestions/" + suggestionId + "/allocate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 여윳돈을_항목에_배분하면_LIVING에서_빠지고_제안이_APPLIED된다() throws Exception {
        long userId = newUser("wf");
        long cycleId = newCycle(userId);
        long living = livingLine(cycleId, 200_000);
        long saving = itemLine(cycleId, 50_000);
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":30000}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        assertThat(planLineRepository.findById(saving).orElseThrow().getPlannedAmount())
                .isEqualTo(80_000L);
        assertThat(planLineRepository.findById(living).orElseThrow().getPlannedAmount())
                .isEqualTo(170_000L);
        assertThat(suggestionRepository.findById(sid).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.APPLIED);
    }

    @Test
    void 부족분만큼_항목을_줄이면_LIVING에_더해진다() throws Exception {
        long userId = newUser("sf");
        long cycleId = newCycle(userId);
        long living = livingLine(cycleId, 50_000);
        long saving = itemLine(cycleId, 300_000);
        long sid = suggestion(userId, cycleId, SuggestionType.SHORTFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":40000}]}")
                .andExpect(status().isOk());

        assertThat(planLineRepository.findById(saving).orElseThrow().getPlannedAmount())
                .isEqualTo(260_000L);
        assertThat(planLineRepository.findById(living).orElseThrow().getPlannedAmount())
                .isEqualTo(90_000L);
    }

    @Test
    void 배분합이_차액을_넘으면_400이고_계획은_불변이다() throws Exception {
        long userId = newUser("over");
        long cycleId = newCycle(userId);
        livingLine(cycleId, 200_000);
        long saving = itemLine(cycleId, 50_000);
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":150000}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(planLineRepository.findById(saving).orElseThrow().getPlannedAmount())
                .isEqualTo(50_000L);
        assertThat(suggestionRepository.findById(sid).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void 이미_이체한_DONE_라인은_대상이_될_수_없다() throws Exception {
        long userId = newUser("done");
        long cycleId = newCycle(userId);
        livingLine(cycleId, 200_000);
        PlanLine item = PlanLine.item(cycleId, null, null, "청년도약계좌", "SAVING", "국민", 50_000);
        item.changeStatus(PlanLineStatus.DONE, Instant.EPOCH);
        long saving = planLineRepository.save(item).getId();
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":30000}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void LIVING_라인은_직접_배분_대상이_될_수_없다() throws Exception {
        long userId = newUser("liv");
        long cycleId = newCycle(userId);
        long living = livingLine(cycleId, 200_000);
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[{\"planLineId\":" + living + ",\"amount\":30000}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 타인의_제안은_배분할_수_없다() throws Exception {
        long ownerId = newUser("owner");
        long cycleId = newCycle(ownerId);
        livingLine(cycleId, 200_000);
        long saving = itemLine(cycleId, 50_000);
        long sid = suggestion(ownerId, cycleId, SuggestionType.WINDFALL, 100_000);
        long otherId = newUser("other");

        allocate(otherId, sid, "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":30000}]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 이미_해소된_제안은_다시_배분할_수_없다() throws Exception {
        long userId = newUser("twice");
        long cycleId = newCycle(userId);
        livingLine(cycleId, 200_000);
        long saving = itemLine(cycleId, 50_000);
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        String body = "{\"allocations\":[{\"planLineId\":" + saving + ",\"amount\":30000}]}";
        allocate(userId, sid, body).andExpect(status().isOk());
        allocate(userId, sid, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUGGESTION_ALREADY_RESOLVED"));
    }

    @Test
    void 빈_배분_목록은_400이다() throws Exception {
        long userId = newUser("empty");
        long cycleId = newCycle(userId);
        livingLine(cycleId, 200_000);
        long sid = suggestion(userId, cycleId, SuggestionType.WINDFALL, 100_000);

        allocate(userId, sid, "{\"allocations\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
