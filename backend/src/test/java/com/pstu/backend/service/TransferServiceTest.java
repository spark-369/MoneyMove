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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityHelper securityHelper;

    @InjectMocks
    private TransferService transferService;

    private User sender;
    private User receiver;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        sender = new User("alice", "alice@example.com", "h", "Alice", "01");
        sender.setId(1L);
        receiver = new User("bob", "bob@example.com", "h", "Bob", "02");
        receiver.setId(2L);

        senderAccount = new Account(sender, new BigDecimal("1000.00"), "BDT");
        senderAccount.setId(10L);
        receiverAccount = new Account(receiver, new BigDecimal("0.00"), "BDT");
        receiverAccount.setId(20L);
    }

    @Test
    void transferMovesMoneyAndRecordsTransaction() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest req = new TransferRequest("bob", new BigDecimal("250.00"), "gift", null);
        TransactionResponse response = transferService.transfer(req);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("750.00");
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("250.00");
        assertThat(response.getAmount()).isEqualByComparingTo("250.00");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.TRANSFER);
    }

    @Test
    void transferRejectsSelfTransfer() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));

        TransferRequest req = new TransferRequest("alice", new BigDecimal("10.00"), null, null);

        assertThatThrownBy(() -> transferService.transfer(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void transferRejectsUnknownRecipient() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        TransferRequest req = new TransferRequest("ghost", new BigDecimal("10.00"), null, null);

        assertThatThrownBy(() -> transferService.transfer(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transferRejectsInsufficientBalance() {
        senderAccount.setBalance(new BigDecimal("100.00"));

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        TransferRequest req = new TransferRequest("bob", new BigDecimal("500.00"), null, null);

        assertThatThrownBy(() -> transferService.transfer(req))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void transferRoundsAmountToTwoDecimals() {
        when(securityHelper.currentUsername()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest req = new TransferRequest("bob", new BigDecimal("33.456"), null, null);
        TransactionResponse response = transferService.transfer(req);

        assertThat(response.getAmount()).isEqualByComparingTo("33.46");
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("966.54");
    }

    @Test
    void transferWithIdempotencyKeyReturnsExistingOnRetry() {
        Transaction existing = new Transaction(10L, 20L, new BigDecimal("50.00"), "BDT",
                TransactionType.TRANSFER, TransactionStatus.COMPLETED, "dup", "key-xyz");
        existing.setReferenceId("ref-existing");

        when(securityHelper.currentUsername()).thenReturn("alice");
        when(transactionRepository.findByIdempotencyKey("key-xyz")).thenReturn(Optional.of(existing));

        TransferRequest req = new TransferRequest("bob", new BigDecimal("50.00"), "dup", "key-xyz");
        TransactionResponse first = transferService.transfer(req);
        TransactionResponse second = transferService.transfer(req);

        assertThat(first.getReferenceId()).isEqualTo("ref-existing");
        assertThat(second.getReferenceId()).isEqualTo("ref-existing");
    }
}
