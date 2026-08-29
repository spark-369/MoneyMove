package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.request.TransferRequest;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TransferService.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransferServiceConcurrencyTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SecurityHelper securityHelper;

    @PersistenceContext
    private EntityManager em;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = userRepository.save(new User("alice", "alice@x.com", "hash", "Alice", "01"));
        receiver = userRepository.save(new User("bob", "bob@x.com", "hash", "Bob", "02"));
        accountRepository.save(new Account(sender, new BigDecimal("1000.00"), "BDT"));
        accountRepository.save(new Account(receiver, new BigDecimal("0.00"), "BDT"));
        em.flush();
        em.clear();
    }

    private void authenticate(String username) {
        UserDetails ud = org.springframework.security.core.userdetails.User.withUsername(username)
                .password("x").authorities("ROLE_USER").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                ud, null, ud.getAuthorities());
        SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void concurrentTransfersDoNotOverdrawAndPreserveConservation() throws InterruptedException {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    authenticate("alice");
                    transferService.transfer(new TransferRequest("bob", new BigDecimal("10.00"), "t", "key-" + Thread.currentThread().getId()));
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Account senderAcc = accountRepository.findByUserId(sender.getId()).orElseThrow();
        Account receiverAcc = accountRepository.findByUserId(receiver.getId()).orElseThrow();

        long completed = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .count();

        assertThat(senderAcc.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00").subtract(new BigDecimal("10.00").multiply(BigDecimal.valueOf(completed))));
        assertThat(receiverAcc.getBalance()).isEqualByComparingTo(new BigDecimal("10.00").multiply(BigDecimal.valueOf(completed)));
        assertThat(senderAcc.getBalance().add(receiverAcc.getBalance())).isEqualByComparingTo("1000.00");
    }

    @Test
    void idempotencyKeyPreventsDuplicateTransfer() {
        authenticate("alice");
        TransferRequest req = new TransferRequest("bob", new BigDecimal("50.00"), "dup", "same-key-123");
        TransactionResponse first = transferService.transfer(req);
        TransactionResponse second = transferService.transfer(req);

        assertThat(first.getReferenceId()).isEqualTo(second.getReferenceId());

        Account senderAcc = accountRepository.findByUserId(sender.getId()).orElseThrow();
        assertThat(senderAcc.getBalance()).isEqualByComparingTo("950.00");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        SecurityHelper securityHelper() {
            return new SecurityHelper();
        }
    }
}
