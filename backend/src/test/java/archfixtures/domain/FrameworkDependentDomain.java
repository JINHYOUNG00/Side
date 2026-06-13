package archfixtures.domain;

import org.springframework.stereotype.Component;

/**
 * RuleEnforcementTest 전용 위반 픽스처 — 규칙 9(domain 패키지 프레임워크 의존 금지)를
 * 일부러 어긴다(스프링 @Component 의존). 패키지명에 domain 이 들어가 ArchUnit의
 * ..domain.. 매칭 대상이 된다. com.ngsoft.salary 밖이라 전역 스캔엔 안 잡힘. 실제 코드 아님.
 */
@Component
public class FrameworkDependentDomain {}
