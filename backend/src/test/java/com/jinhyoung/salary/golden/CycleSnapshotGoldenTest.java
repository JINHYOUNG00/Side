package com.jinhyoung.salary.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.cycle.domain.CycleSnapshotBuilder;
import com.jinhyoung.salary.cycle.domain.PlanLineDraft;
import com.jinhyoung.salary.cycle.domain.PlanLineType;
import com.jinhyoung.salary.cycle.domain.WaterfallLine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 사이클 스냅샷 골든 테스트(FLOW-03) — CycleSnapshotBuilder가 노션 실데이터 fixture로
 * plan_line을 정확히 산출하는지 강제한다. FLOW-03 verify("골든에서 LIVING planned_amount 일치")의 본체.
 *
 * <p>WaterfallGoldenTest와 <b>같은 fixture(waterfall-cases.json)를 소비</b>한다 — 기존 필드만 읽고
 * (lines·expected.living·expected.shortfall) 새 기대값을 추가하지 않으므로 goldenLock이 필요 없다.
 * fixture는 절대 수정 금지(깨지면 코드가 틀린 것).
 *
 * <p>Jackson 의존이라 ..domain.. 밖(golden 패키지)에 둔다(WaterfallGoldenTest와 동일 배치).
 */
class CycleSnapshotGoldenTest {

    private static final Path FIXTURE = Path.of("src/test/resources/golden/waterfall-cases.json");

    /** 골든 fixture엔 생활비 통장이 없으므로 테스트가 임의의 활성 통장 id를 주입한다. */
    private static final long LIVING_ACCOUNT = 7L;

    @TestFactory
    List<DynamicTest> snapshotPlanLinesMatchGoldenFixture() throws Exception {
        JsonNode root = new ObjectMapper().readTree(FIXTURE.toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            JsonNode input = c.get("input");
            JsonNode expected = c.get("expected");

            long income = input.get("income").asLong();
            long envelopeContribution = input.get("envelopeContribution").asLong();
            List<WaterfallLine> lines = new ArrayList<>();
            for (JsonNode l : input.get("lines")) {
                lines.add(new WaterfallLine(
                        l.get("budgetItemId").asLong(),
                        Category.valueOf(l.get("category").asText()),
                        l.get("amount").asLong()));
            }
            long expectedLiving = expected.get("living").asLong();
            boolean expectedShortfall = expected.get("shortfall").asBoolean();

            tests.add(DynamicTest.dynamicTest(name, () -> {
                List<PlanLineDraft> drafts =
                        CycleSnapshotBuilder.build(income, lines, envelopeContribution, LIVING_ACCOUNT);

                // ITEM 라인: 입력 라인마다 1건, 입력 순서·budgetItemId·카테고리·금액이 그대로다(EMERGENCY 포함).
                List<PlanLineDraft> itemDrafts = drafts.stream()
                        .filter(d -> d.lineType() == PlanLineType.ITEM)
                        .toList();
                assertThat(itemDrafts).as("ITEM 라인 수 = 입력 라인 수: %s", name).hasSize(lines.size());
                for (int i = 0; i < lines.size(); i++) {
                    WaterfallLine in = lines.get(i);
                    PlanLineDraft d = itemDrafts.get(i);
                    assertThat(d.budgetItemId())
                            .as("ITEM %d budgetItemId·순서: %s", i, name)
                            .isEqualTo(in.budgetItemId());
                    assertThat(d.category()).as("ITEM %d 카테고리: %s", i, name).isEqualTo(in.category());
                    assertThat(d.plannedAmount()).as("ITEM %d 금액: %s", i, name).isEqualTo(in.amount());
                }

                // LIVING 라인: 과배분이 아니고 living>0인 케이스에서 planned_amount = expected.living(생활비 356,107).
                List<PlanLineDraft> livingDrafts = drafts.stream()
                        .filter(d -> d.lineType() == PlanLineType.LIVING)
                        .toList();
                if (!expectedShortfall && expectedLiving > 0) {
                    assertThat(livingDrafts).as("LIVING 라인 1건: %s", name).hasSize(1);
                    PlanLineDraft living = livingDrafts.get(0);
                    assertThat(living.plannedAmount())
                            .as("LIVING planned_amount = 골든 생활비: %s", name)
                            .isEqualTo(expectedLiving);
                    assertThat(living.accountId())
                            .as("LIVING accountId = 생활비 통장: %s", name)
                            .isEqualTo(LIVING_ACCOUNT);
                } else {
                    assertThat(livingDrafts)
                            .as("과배분/0원이면 LIVING 라인 없음: %s", name)
                            .isEmpty();
                }

                // 불변식: 봉투 라인이 아직 없으므로 Σ(plan_lines) = income − envelopeContribution.
                long sum =
                        drafts.stream().mapToLong(PlanLineDraft::plannedAmount).sum();
                assertThat(sum).as("plan_line 합 = income − 봉투적립: %s", name).isEqualTo(income - envelopeContribution);
            }));
        }
        assertThat(tests).as("골든 케이스가 비어 있으면 안 된다").isNotEmpty();
        return tests;
    }
}
