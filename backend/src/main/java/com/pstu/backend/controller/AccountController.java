package com.pstu.backend.controller;

import com.pstu.backend.dto.response.BalanceResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.ForbiddenException;
import com.pstu.backend.security.SecurityHelper;
import com.pstu.backend.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final SecurityHelper securityHelper;

    public AccountController(AccountService accountService, SecurityHelper securityHelper) {
        this.accountService = accountService;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/me/balance")
    public ResponseEntity<BalanceResponse> myBalance() {
        return ResponseEntity.ok(accountService.myBalance());
    }

    @GetMapping("/balance/{username}")
    public ResponseEntity<BalanceResponse> balance(@PathVariable String username) {
        if (!securityHelper.currentUsername().equals(username)) {
            throw new ForbiddenException("You are not allowed to view another user's balance");
        }
        return ResponseEntity.ok(accountService.getBalance(username));
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<TransactionResponse>> history() {
        return ResponseEntity.ok(accountService.history());
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<AccountService.UserSummary>> search(@RequestParam String q) {
        return ResponseEntity.ok(accountService.searchUsers(q));
    }
}
