package com.pstu.backend.service;

import com.pstu.backend.domain.Account;
import com.pstu.backend.domain.Transaction;
import com.pstu.backend.domain.TransactionStatus;
import com.pstu.backend.domain.TransactionType;
import com.pstu.backend.domain.User;
import com.pstu.backend.dto.request.LoginRequest;
import com.pstu.backend.dto.request.RegisterRequest;
import com.pstu.backend.dto.response.AuthResponse;
import com.pstu.backend.exception.ConflictException;
import com.pstu.backend.exception.ResourceNotFoundException;
import com.pstu.backend.repository.AccountRepository;
import com.pstu.backend.repository.TransactionRepository;
import com.pstu.backend.repository.UserRepository;
import com.pstu.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private final BigDecimal initialBalance = new BigDecimal("100000.00");
    private final String currency = "BDT";

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, accountRepository, transactionRepository,
                passwordEncoder, authenticationManager, jwtUtil, initialBalance, currency);
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("alice");
        registerRequest.setEmail("alice@example.com");
        registerRequest.setPassword("secret123");
        registerRequest.setFullName("Alice Khatun");
        registerRequest.setPhoneNumber("01700000000");
    }

    @Test
    void registerCreatesUserAccountAndFundingTransaction() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(jwtUtil.generateToken("alice")).thenReturn("jwt-token");

        User savedUser = new User("alice", "alice@example.com", "hashed", "Alice Khatun", "01700000000");
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Account savedAccount = new Account(savedUser, initialBalance, currency);
        savedAccount.setId(1L);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getBalance()).isEqualByComparingTo("100000.00");
        assertThat(response.getCurrency()).isEqualTo("BDT");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo("100000.00");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction funding = txCaptor.getValue();
        assertThat(funding.getType()).isEqualTo(TransactionType.INITIAL_FUNDING);
        assertThat(funding.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(funding.getAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void loginSucceedsAndReturnsToken() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("secret123");

        User user = new User("alice", "alice@example.com", "hashed", "Alice", "01");
        user.setId(1L);
        Account account = new Account(user, new BigDecimal("500.00"), "BDT");
        account.setId(1L);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", "secret123"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(jwtUtil.generateToken("alice")).thenReturn("login-jwt");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getToken()).isEqualTo("login-jwt");
        assertThat(response.getBalance()).isEqualByComparingTo("500.00");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void loginFailsOnBadCredentials() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("wrong");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void loginFailsWhenUserMissing() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("ghost");
        loginRequest.setPassword("x");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("ghost", "x"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
