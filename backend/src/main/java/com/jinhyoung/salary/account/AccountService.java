package com.jinhyoung.salary.account;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통장 CRUD 유스케이스(SET-04). 모든 변경·조회는 호출 사용자의 소유분에 한정한다 —
 * 소유권 검증을 이 한 곳(+리포지토리 쿼리)으로 모아 컨트롤러가 우회할 수 없게 한다(API명세 8장).
 */
@Service
public class AccountService {

    /** 활성 통장 개수 상한(구현규칙 6장). */
    static final long MAX_ACTIVE_ACCOUNTS = 20;

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> list(long userId) {
        return accountRepository.findByUserIdAndActiveTrueOrderBySortOrderAsc(userId);
    }

    @Transactional
    public Account create(long userId, String name, String purpose, String bankDeepLink) {
        if (accountRepository.countByUserIdAndActiveTrue(userId) >= MAX_ACTIVE_ACCOUNTS) {
            throw new ApiException(ErrorCode.ACCOUNT_LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_ACCOUNTS));
        }
        int sortOrder = accountRepository.maxSortOrder(userId) + 1;
        return accountRepository.save(Account.create(userId, name, purpose, bankDeepLink, sortOrder));
    }

    @Transactional
    public Account update(
            long userId, long accountId, String name, String purpose, String bankDeepLink, Integer sortOrder) {
        Account account = ownedOrThrow(userId, accountId);
        account.update(name, purpose, bankDeepLink, sortOrder);
        return account;
    }

    @Transactional
    public void delete(long userId, long accountId) {
        Account account = ownedOrThrow(userId, accountId);
        account.deactivate();
    }

    /** 소유권 + 활성 검증의 단일 관문 — 미소유·삭제·부재는 모두 NOT_FOUND(존재 여부 비노출). */
    private Account ownedOrThrow(long userId, long accountId) {
        return accountRepository
                .findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "account", "id", accountId)));
    }
}
