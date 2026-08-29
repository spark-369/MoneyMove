package com.pstu.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_reference", columnList = "referenceId"),
        @Index(name = "idx_tx_sender", columnList = "senderAccountId"),
        @Index(name = "idx_tx_receiver", columnList = "receiverAccountId")
})
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String referenceId;

    @Column(nullable = false)
    private Long senderAccountId;

    @Column(nullable = false)
    private Long receiverAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Transaction(Long senderAccountId, Long receiverAccountId, BigDecimal amount,
                       String currency, TransactionType type, TransactionStatus status,
                       String description, String idempotencyKey) {
        this.referenceId = UUID.randomUUID().toString();
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
    }
}
