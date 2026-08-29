package com.pstu.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "money_requests", indexes = {
        @Index(name = "idx_req_requester", columnList = "requesterAccountId"),
        @Index(name = "idx_req_debtor", columnList = "debtorAccountId")
})
@Getter
@Setter
@NoArgsConstructor
public class MoneyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requesterAccountId;

    @Column(nullable = false)
    private Long debtorAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoneyRequestStatus status;

    @Column
    private Long settledTransactionId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant resolvedAt;

    public MoneyRequest(Long requesterAccountId, Long debtorAccountId, BigDecimal amount,
                        String currency, String note) {
        this.requesterAccountId = requesterAccountId;
        this.debtorAccountId = debtorAccountId;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
        this.status = MoneyRequestStatus.PENDING;
    }
}
