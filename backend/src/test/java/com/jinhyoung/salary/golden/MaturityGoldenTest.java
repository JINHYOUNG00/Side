package com.jinhyoung.salary.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.budgetitem.domain.MaturityCalculator;
import com.jinhyoung.salary.budgetitem.domain.MaturityInput;
import com.jinhyoung.salary.budgetitem.domain.MaturityResult;
import com.jinhyoung.salary.budgetitem.domain.TaxType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 골든 테스트 — 검증된 실데이터 fixture(maturity-cases.json)를 MaturityCalculator가
 * 정확히 재현하는지 강제한다. fixture는 절대 수정 금지(깨지면 코드가 틀린 것).
 * 적금 만기 3,731,976 / 2,476,986 케이스가 여기서 실제로 검증된다.
 *
 * <p>이 테스트는 Jackson에 의존하므로 ..domain.. 밖(golden 패키지)에 둔다 — 도메인
 * 순수성 ArchUnit 규칙이 테스트 클래스까지 스캔하기 때문.
 */
class MaturityGoldenTest {

    private static final Path FIXTURE = Path.of("src/test/resources/golden/maturity-cases.json");

    @TestFactory
    List<DynamicTest> maturityMatchesGoldenFixture() throws Exception {
        JsonNode root = new ObjectMapper().readTree(FIXTURE.toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            JsonNode input = c.get("input");
            String name = c.get("name").asText();
            MaturityInput in = new MaturityInput(
                    input.get("monthlyAmount").asLong(),
                    new BigDecimal(input.get("annualRatePct").asText()),
                    input.get("months").asInt(),
                    TaxType.valueOf(input.get("taxType").asText()));
            long expected = c.get("expectedMaturityAmount").asLong();
            tests.add(DynamicTest.dynamicTest(name, () -> {
                MaturityResult result = MaturityCalculator.calculate(in);
                assertThat(result.total()).as("만기금액이 골든 기대값과 일치해야 한다: %s", name).isEqualTo(expected);
                assertThat(result.principal() + result.interest() - result.tax())
                        .as("분해값 합(원금+이자−세금)이 total과 일치")
                        .isEqualTo(result.total());
            }));
        }
        assertThat(tests).as("골든 케이스가 비어 있으면 안 된다").isNotEmpty();
        return tests;
    }
}
