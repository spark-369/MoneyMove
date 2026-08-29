package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.response.BalanceResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.ResourceNotFoundException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityHelper securityHelper;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          SecurityHelper securityHelper) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.securityHelper = securityHelper;
    }

    @Cacheable(cacheNames = "balances", key = "#username")
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return new BalanceResponse(account.getBalance(), account.getCurrency());
    }

    @Transactional(readOnly = true)
    public BalanceResponse myBalance() {
        return getBalance(securityHelper.currentUsername());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> history() {
        String username = securityHelper.currentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return transactionRepository.findHistoryForAccount(account.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> searchUsers(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String like = "%" + query.trim().toLowerCase() + "%";
        return userRepository.findAll().stream()
                .filter(u -> u.getUsername().toLowerCase().contains(query.trim().toLowerCase())
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(query.trim().toLowerCase())))
                .limit(10)
                .map(u -> new UserSummary(u.getUsername(), u.getFullName()))
                .collect(Collectors.toList());
    }

    public static class UserSummary {
        private final String username;
        private final String fullName;

        public UserSummary(String username, String fullName) {
            this.username = username;
            this.fullName = fullName;
        }

        public String getUsername() {
            return username;
        }

        public String getFullName() {
            return fullName;
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        User sender = userRepository.findById(tx.getSenderAccountId()).orElse(null);
        User receiver = userRepository.findById(tx.getReceiverAccountId()).orElse(null);
        return new TransactionResponse(
                tx.getReferenceId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getSenderAccountId(),
                tx.getReceiverAccountId(),
                sender != null ? sender.getUsername() : null,
                receiver != null ? receiver.getUsername() : null,
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getCreatedAt());
    }
}
