package com.jinhyoung.salary.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CLAUDE.md 절대 규칙의 기계 강제 (verify에 포함되어 항상 실행).
 * 규칙 2: 금액은 long 원 단위 — double/float 금지.
 * 규칙 9: domain 패키지는 프레임워크 의존 금지 — 순수 자바.
 */
@AnalyzeClasses(packages = "com.jinhyoung.salary")
class ArchitectureTest {

    /** 규칙 2 — 프로젝트 전체에서 double/float 필드 금지. 비율·중간계산은 BigDecimal. */
    @ArchTest
    static final ArchRule noDoubleOrFloatFields = noFields()
            .should()
            .haveRawType(double.class)
            .orShould()
            .haveRawType(float.class)
            .orShould()
            .haveRawType(Double.class)
            .orShould()
            .haveRawType(Float.class)
            .because("금액은 long 원 단위, 중간 계산은 BigDecimal (CLAUDE.md 규칙 2)");

    /** 규칙 9 — domain 패키지는 스프링/JPA/Jackson에 의존하지 않는 순수 자바. */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..")
            .allowEmptyShould(true) // domain 패키지가 생기기 전(Phase 0)에도 verify 초록 유지
            .because("도메인 계산 로직은 의존성 없는 순수 클래스 (CLAUDE.md 규칙 9)");
}
