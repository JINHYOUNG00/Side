package com.jinhyoung.salary.account.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 통장(ERD accounts, SET-04). 엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만.
 *
 * <p>별칭·용도·은행 딥링크만 보관한다. 계좌번호 등 금융 식별 정보는 저장하지 않는다(CLAUDE.md 규칙 6) —
 * 컬럼 추가도 금지. soft delete는 {@code is_active}로만(규칙 5): 과거 스냅샷·plan_lines가 참조를 유지한다.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String purpose;

    @Column(name = "bank_deep_link")
    private String bankDeepLink;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Account() {
        // JPA
    }

    private Account(Long userId, String name, String purpose, String bankDeepLink, int sortOrder) {
        this.userId = userId;
        this.name = name;
        this.purpose = purpose;
        this.bankDeepLink = bankDeepLink;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    /** 새 통장 생성. sortOrder는 호출 측(서비스)이 사용자별 끝자리로 부여한다. */
    public static Account create(Long userId, String name, String purpose, String bankDeepLink, int sortOrder) {
        return new Account(userId, name, purpose, bankDeepLink, sortOrder);
    }

    /** 별칭·용도·딥링크를 갱신한다. sortOrder는 null이면 유지(재정렬은 값이 올 때만). */
    public void update(String name, String purpose, String bankDeepLink, Integer sortOrder) {
        this.name = name;
        this.purpose = purpose;
        this.bankDeepLink = bankDeepLink;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    /** soft delete — 물리 삭제는 회원 탈퇴 cascade뿐(규칙 5). */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getBankDeepLink() {
        return bankDeepLink;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
