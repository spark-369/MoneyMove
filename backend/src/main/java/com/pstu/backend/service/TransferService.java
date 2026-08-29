package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.TransactionType;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.request.TransferRequest;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.BadRequestException;
import com.pstu.backend.exception.InsufficientBalanceException;
import com.pstu.backend.exception.ResourceNotFoundException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SecurityHelper securityHelper;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           UserRepository userRepository,
                           SecurityHelper securityHelper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.securityHelper = securityHelper;
    }

    @CacheEvict(cacheNames = "balances", allEntries = true)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse transfer(TransferRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());

        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        String senderUsername = securityHelper.currentUsername();
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));

        if (sender.getUsername().equals(request.getToUsername())) {
            throw new BadRequestException("You cannot transfer money to yourself");
        }

        User receiver = userRepository.findByUsername(request.getToUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found: " + request.getToUsername()));

        BigDecimal amount = request.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transfer amount must be positive");
        }

        Account senderAccount = accountRepository.findByUserIdForUpdate(sender.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));
        Account receiverAccount = accountRepository.findByUserIdForUpdate(receiver.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver account not found"));

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + senderAccount.getBalance() + " " + senderAccount.getCurrency());
        }

        senderAccount.debit(amount);
        receiverAccount.credit(amount);
        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        Transaction tx = new Transaction(
                senderAccount.getId(),
                receiverAccount.getId(),
                amount,
                senderAccount.getCurrency(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                request.getDescription(),
                key);
        Transaction saved = transactionRepository.save(tx);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByReference(String referenceId) {
        Transaction tx = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + referenceId));
        return toResponse(tx);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
