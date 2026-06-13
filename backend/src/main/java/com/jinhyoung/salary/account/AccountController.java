package com.jinhyoung.salary.account;

import com.jinhyoung.salary.account.infra.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * 통장 CRUD(SET-04, API명세 4장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 소유분만 다룬다.
 * DELETE는 물리 삭제가 아닌 is_active=false(soft delete, 규칙 5).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    /** name/purpose/bankDeepLink 길이 상한 — ERD 컬럼 길이 및 구현규칙 6장과 일치. */
    private static final int NAME_MAX = 50;

    private static final int PURPOSE_MAX = 100;
    private static final int DEEP_LINK_MAX = 500;

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Long userId) {
        return accountService.list(userId).stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateRequest request) {
        Account account = accountService.create(userId, request.name(), request.purpose(), request.bankDeepLink());
        return AccountResponse.from(account);
    }

    @PatchMapping("/{id}")
    public AccountResponse update(
            @AuthenticationPrincipal Long userId, @PathVariable long id, @Valid @RequestBody UpdateRequest request) {
        Account account = accountService.update(
                userId, id, request.name(), request.purpose(), request.bankDeepLink(), request.sortOrder());
        return AccountResponse.from(account);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        accountService.delete(userId, id);
    }

    public record CreateRequest(
            @NotBlank @Size(max = NAME_MAX) String name,
            @Size(max = PURPOSE_MAX) String purpose,
            @Size(max = DEEP_LINK_MAX) String bankDeepLink) {}

    public record UpdateRequest(
            @NotBlank @Size(max = NAME_MAX) String name,
            @Size(max = PURPOSE_MAX) String purpose,
            @Size(max = DEEP_LINK_MAX) String bankDeepLink,
            Integer sortOrder) {}

    public record AccountResponse(Long id, String name, String purpose, String bankDeepLink, int sortOrder) {
        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getName(),
                    account.getPurpose(),
                    account.getBankDeepLink(),
                    account.getSortOrder());
        }
    }
}
