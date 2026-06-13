package archfixtures;

/**
 * RuleEnforcementTest 전용 위반 픽스처 — 규칙 2(double/float 금지)를 일부러 어긴다.
 * com.jinhyoung.salary 밖에 있어 전역 ArchitectureTest 스캔에는 잡히지 않고,
 * 음성 테스트가 명시적으로 임포트해 규칙이 위반을 검출하는지 증명한다. 실제 코드 아님.
 */
public class MoneyFieldViolation {
    double amount;
}
