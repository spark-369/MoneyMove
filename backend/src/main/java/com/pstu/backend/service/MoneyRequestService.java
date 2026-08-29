package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.MoneyRequest;
import com.pstu.backend.domain.MoneyRequestStatus;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.TransactionType;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.request.MoneyRequestCreateRequest;
import com.pstu.backend.dto.response.MoneyRequestResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.BadRequestException;
import com.pstu.backend.exception.InsufficientBalanceException;
import com.pstu.backend.exception.ResourceNotFoundException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.MoneyRequestRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MoneyRequestService {

    private final MoneyRequestRepository moneyRequestRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SecurityHelper securityHelper;

    public MoneyRequestService(MoneyRequestRepository moneyRequestRepository,
                               AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               UserRepository userRepository,
                               SecurityHelper securityHelper) {
        this.moneyRequestRepository = moneyRequestRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.securityHelper = securityHelper;
    }

    @Transactional
    public MoneyRequestResponse createRequest(MoneyRequestCreateRequest request) {
        String requesterUsername = securityHelper.currentUsername();
        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Requester account not found"));

        if (requester.getUsername().equals(request.getFromUsername())) {
            throw new BadRequestException("You cannot request money from yourself");
        }

        User debtor = userRepository.findByUsername(request.getFromUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getFromUsername()));

        BigDecimal amount = request.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Request amount must be positive");
        }

        Account requesterAccount = accountRepository.findByUserId(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester account not found"));
        Account debtorAccount = accountRepository.findByUserId(debtor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Debtor account not found"));

        MoneyRequest moneyRequest = new MoneyRequest(
                requesterAccount.getId(), debtorAccount.getId(), amount,
                requesterAccount.getCurrency(), request.getNote());
        MoneyRequest saved = moneyRequestRepository.save(moneyRequest);
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse acceptRequest(Long requestId) {
        String debtorUsername = securityHelper.currentUsername();
        User debtor = userRepository.findByUsername(debtorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        MoneyRequest request = moneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found"));

        if (!request.getDebtorAccountId().equals(debtorAccountIdOf(debtor))) {
            throw new BadRequestException("You are not authorized to pay this request");
        }
        if (request.getStatus() != MoneyRequestStatus.PENDING) {
            throw new BadRequestException("This request is no longer pending (status: " + request.getStatus() + ")");
        }

        Account debtorAccount = accountRepository.findByUserIdForUpdate(debtor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Debtor account not found"));
        Account requesterAccount = accountRepository.findByIdForUpdate(request.getRequesterAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester account not found"));

        if (debtorAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance to pay this request. Available: " + debtorAccount.getBalance());
        }

        debtorAccount.debit(request.getAmount());
        requesterAccount.credit(request.getAmount());
        accountRepository.save(debtorAccount);
        accountRepository.save(requesterAccount);

        Transaction tx = new Transaction(
                debtorAccount.getId(),
                requesterAccount.getId(),
                request.getAmount(),
                request.getCurrency(),
                TransactionType.REQUEST_SETTLEMENT,
                TransactionStatus.COMPLETED,
                "Settled money request #" + request.getId(),
                UUID.randomUUID().toString());
        Transaction saved = transactionRepository.save(tx);

        request.setStatus(MoneyRequestStatus.ACCEPTED);
        request.setResolvedAt(java.time.Instant.now());
        request.setSettledTransactionId(saved.getId());
        moneyRequestRepository.save(request);

        return toTransactionResponse(saved);
    }

    @Transactional
    public MoneyRequestResponse declineRequest(Long requestId) {
        String debtorUsername = securityHelper.currentUsername();
        User debtor = userRepository.findByUsername(debtorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        MoneyRequest request = moneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found"));

        if (!request.getDebtorAccountId().equals(debtorAccountIdOf(debtor))) {
            throw new BadRequestException("You are not authorized to act on this request");
        }
        if (request.getStatus() != MoneyRequestStatus.PENDING) {
            throw new BadRequestException("This request is no longer pending");
        }

        request.setStatus(MoneyRequestStatus.DECLINED);
        request.setResolvedAt(java.time.Instant.now());
        MoneyRequest saved = moneyRequestRepository.save(request);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MoneyRequestResponse> listIncoming() {
        String username = securityHelper.currentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return moneyRequestRepository.findPendingForDebtor(account.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MoneyRequestResponse> listOutgoing() {
        String username = securityHelper.currentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return moneyRequestRepository.findPendingForRequester(account.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Long debtorAccountIdOf(User debtor) {
        return accountRepository.findByUserId(debtor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found")).getId();
    }

    private MoneyRequestResponse toResponse(MoneyRequest r) {
        User requester = userRepository.findById(r.getRequesterAccountId()).orElse(null);
        User debtor = userRepository.findById(r.getDebtorAccountId()).orElse(null);
        return new MoneyRequestResponse(
                r.getId(),
                r.getRequesterAccountId(),
                r.getDebtorAccountId(),
                requester != null ? requester.getUsername() : null,
                debtor != null ? debtor.getUsername() : null,
                r.getAmount(),
                r.getCurrency(),
                r.getNote(),
                r.getStatus().name(),
                r.getCreatedAt(),
                r.getResolvedAt());
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
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
