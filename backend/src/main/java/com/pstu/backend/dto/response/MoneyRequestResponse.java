package com.pstu.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public class MoneyRequestResponse {
    private Long id;
    private Long requesterAccountId;
    private Long debtorAccountId;
    private String requesterUsername;
    private String debtorUsername;
    private BigDecimal amount;
    private String currency;
    private String note;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;

    public MoneyRequestResponse() {
    }

    public MoneyRequestResponse(Long id, Long requesterAccountId, Long debtorAccountId, String requesterUsername,
                                 String debtorUsername, BigDecimal amount, String currency, String note,
                                 String status, Instant createdAt, Instant resolvedAt) {
        this.id = id;
        this.requesterAccountId = requesterAccountId;
        this.debtorAccountId = debtorAccountId;
        this.requesterUsername = requesterUsername;
        this.debtorUsername = debtorUsername;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequesterAccountId() {
        return requesterAccountId;
    }

    public void setRequesterAccountId(Long requesterAccountId) {
        this.requesterAccountId = requesterAccountId;
    }

    public Long getDebtorAccountId() {
        return debtorAccountId;
    }

    public void setDebtorAccountId(Long debtorAccountId) {
        this.debtorAccountId = debtorAccountId;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public void setRequesterUsername(String requesterUsername) {
        this.requesterUsername = requesterUsername;
    }

    public String getDebtorUsername() {
        return debtorUsername;
    }

    public void setDebtorUsername(String debtorUsername) {
        this.debtorUsername = debtorUsername;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
