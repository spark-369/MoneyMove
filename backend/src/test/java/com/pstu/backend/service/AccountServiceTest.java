package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.TransactionType;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.response.BalanceResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.exception.ResourceNotFoundException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SecurityHelper securityHelper;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@example.com", "h", "Alice", "01");
        user.setId(1L);
        account = new Account(user, new BigDecimal("250.00"), "BDT");
        account.setId(10L);
    }

    @Test
    void getBalanceReturnsCorrectBalance() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.getBalance("alice");

        assertThat(response.getBalance()).isEqualByComparingTo("250.00");
        assertThat(response.getCurrency()).isEqualTo("BDT");
    }

    @Test
    void getBalanceThrowsWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance("ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myBalanceDelegatesToCurrentUser() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.myBalance();

        assertThat(response.getBalance()).isEqualByComparingTo("250.00");
    }

    @Test
    void historyReturnsTransactionsForAccount() {
        Transaction tx = new Transaction(10L, 20L, new BigDecimal("50.00"), "BDT",
                TransactionType.TRANSFER, TransactionStatus.COMPLETED, "note", "key");
        tx.setReferenceId("ref-1");

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findHistoryForAccount(10L)).thenReturn(List.of(tx));

        List<TransactionResponse> history = accountService.history();

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getReferenceId()).isEqualTo("ref-1");
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void searchUsersRequiresMinimumTwoChars() {
        assertThat(accountService.searchUsers("a")).isEmpty();
        assertThat(accountService.searchUsers(null)).isEmpty();
        assertThat(accountService.searchUsers("  ")).isEmpty();
    }

    @Test
    void searchUsersMatchesByUsernameAndFullName() {
        User u1 = new User("alice", "alice@example.com", "h", "Alice Khatun", "01");
        u1.setId(1L);
        User u2 = new User("bob", "bob@example.com", "h", "Bob Rahman", "02");
        u2.setId(2L);

        when(userRepository.search("%ali%", PageRequest.of(0, 10))).thenReturn(List.of(u1));
        when(userRepository.search("%rah%", PageRequest.of(0, 10))).thenReturn(List.of(u2));

        assertThat(accountService.searchUsers("ali")).extracting(AccountService.UserSummary::getUsername)
                .containsExactly("alice");
        assertThat(accountService.searchUsers("rah")).extracting(AccountService.UserSummary::getUsername)
                .containsExactly("bob");
    }

    @Test
    void historyResolvesUsernamesFromAccountIds() {
        Transaction tx = new Transaction(10L, 20L, new BigDecimal("50.00"), "BDT",
                TransactionType.TRANSFER, TransactionStatus.COMPLETED, "note", "key");
        tx.setReferenceId("ref-2");

        User receiver = new User("bob", "bob@example.com", "h", "Bob", "02");
        receiver.setId(2L);

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findHistoryForAccount(10L)).thenReturn(List.of(tx));
        when(userRepository.findByAccountId(10L)).thenReturn(Optional.of(user));
        when(userRepository.findByAccountId(20L)).thenReturn(Optional.of(receiver));

        List<TransactionResponse> history = accountService.history();

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSenderUsername()).isEqualTo("alice");
        assertThat(history.get(0).getReceiverUsername()).isEqualTo("bob");
    }
}
