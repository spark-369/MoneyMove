package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.MoneyRequest;
import com.pstu.backend.domain.MoneyRequestStatus;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.request.MoneyRequestCreateRequest;
import com.pstu.backend.dto.response.MoneyRequestResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.BadRequestException;
import com.pstu.backend.exception.InsufficientBalanceException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.MoneyRequestRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoneyRequestServiceTest {

    @Mock
    private MoneyRequestRepository moneyRequestRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityHelper securityHelper;

    @InjectMocks
    private MoneyRequestService moneyRequestService;

    private User requester;
    private User debtor;
    private Account requesterAccount;
    private Account debtorAccount;

    @BeforeEach
    void setUp() {
        requester = new User("bob", "bob@example.com", "h", "Bob", "01");
        requester.setId(1L);
        debtor = new User("alice", "alice@example.com", "h", "Alice", "02");
        debtor.setId(2L);

        requesterAccount = new Account(requester, new BigDecimal("0.00"), "BDT");
        requesterAccount.setId(10L);
        debtorAccount = new Account(debtor, new BigDecimal("500.00"), "BDT");
        debtorAccount.setId(20L);
    }

    @Test
    void createRequestPersistsPendingRequest() {
        when(securityHelper.currentUsername()).thenReturn("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(requester));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(requesterAccount));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));

        MoneyRequest saved = new MoneyRequest(10L, 20L, new BigDecimal("1200.00"), "BDT", "lunch");
        saved.setId(99L);
        when(moneyRequestRepository.save(any(MoneyRequest.class))).thenReturn(saved);

        MoneyRequestCreateRequest req = new MoneyRequestCreateRequest();
        req.setFromUsername("alice");
        req.setAmount(new BigDecimal("1200.00"));
        req.setNote("lunch");

        MoneyRequestResponse response = moneyRequestService.createRequest(req);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getStatus()).isEqualTo(MoneyRequestStatus.PENDING.name());
        assertThat(response.getAmount()).isEqualByComparingTo("1200.00");

        ArgumentCaptor<MoneyRequest> captor = ArgumentCaptor.forClass(MoneyRequest.class);
        verify(moneyRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MoneyRequestStatus.PENDING);
    }

    @Test
    void createRequestRejectsSelfRequest() {
        when(securityHelper.currentUsername()).thenReturn("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(requester));

        MoneyRequestCreateRequest req = new MoneyRequestCreateRequest();
        req.setFromUsername("bob");
        req.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> moneyRequestService.createRequest(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void createRequestRejectsNonPositiveAmount() {
        when(securityHelper.currentUsername()).thenReturn("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(requester));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));

        MoneyRequestCreateRequest req = new MoneyRequestCreateRequest();
        req.setFromUsername("alice");
        req.setAmount(new BigDecimal("0.00"));

        assertThatThrownBy(() -> moneyRequestService.createRequest(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void acceptRequestMovesMoneyAndMarksAccepted() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));
        when(moneyRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest()));
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(debtorAccount));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(requesterAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = moneyRequestService.acceptRequest(99L);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(debtorAccount.getBalance()).isEqualByComparingTo("380.00");
        assertThat(requesterAccount.getBalance()).isEqualByComparingTo("120.00");
        verify(moneyRequestRepository).save(any(MoneyRequest.class));
    }

    @Test
    void acceptRequestRejectsUnauthorizedDebtor() {
        User stranger = new User("eve", "eve@example.com", "h", "Eve", "03");
        stranger.setId(3L);
        Account strangerAccount = new Account(stranger, new BigDecimal("999.00"), "BDT");
        strangerAccount.setId(30L);

        when(securityHelper.currentUsername()).thenReturn("eve");
        when(userRepository.findByUsername("eve")).thenReturn(Optional.of(stranger));
        when(accountRepository.findByUserId(3L)).thenReturn(Optional.of(strangerAccount));
        when(moneyRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> moneyRequestService.acceptRequest(99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("authorized");
    }

    @Test
    void acceptRequestRejectsAlreadyResolved() {
        MoneyRequest resolved = pendingRequest();
        resolved.setStatus(MoneyRequestStatus.ACCEPTED);

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));
        when(moneyRequestRepository.findById(99L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> moneyRequestService.acceptRequest(99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void acceptRequestRejectsInsufficientBalance() {
        debtorAccount.setBalance(new BigDecimal("100.00"));

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));
        when(moneyRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest()));
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(debtorAccount));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(requesterAccount));

        assertThatThrownBy(() -> moneyRequestService.acceptRequest(99L))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void declineRequestMarksDeclined() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));
        when(moneyRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest()));
        when(moneyRequestRepository.save(any(MoneyRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        MoneyRequestResponse response = moneyRequestService.declineRequest(99L);

        assertThat(response.getStatus()).isEqualTo(MoneyRequestStatus.DECLINED.name());
    }

    @Test
    void listIncomingReturnsPendingForDebtor() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(debtor));
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(debtorAccount));
        when(moneyRequestRepository.findPendingForDebtor(20L)).thenReturn(List.of(pendingRequest()));
        when(userRepository.findByAccountId(10L)).thenReturn(Optional.of(requester));
        when(userRepository.findByAccountId(20L)).thenReturn(Optional.of(debtor));

        List<MoneyRequestResponse> incoming = moneyRequestService.listIncoming();

        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).getDebtorUsername()).isEqualTo("alice");
    }

    private MoneyRequest pendingRequest() {
        MoneyRequest r = new MoneyRequest(10L, 20L, new BigDecimal("120.00"), "BDT", "lunch");
        r.setId(99L);
        r.setStatus(MoneyRequestStatus.PENDING);
        return r;
    }
}
