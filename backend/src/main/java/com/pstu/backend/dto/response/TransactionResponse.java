package com.pstu.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {
    private String referenceId;
    private String type;
    private String status;
    private Long senderAccountId;
    private Long receiverAccountId;
    private String senderUsername;
    private String receiverUsername;
    private BigDecimal amount;
    private String currency;
    private String description;
    private Instant createdAt;

    public TransactionResponse() {
    }

    public TransactionResponse(String referenceId, String type, String status, Long senderAccountId,
                               Long receiverAccountId, String senderUsername, String receiverUsername,
                               BigDecimal amount, String currency, String description, Instant createdAt) {
        this.referenceId = referenceId;
        this.type = type;
        this.status = status;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(Long senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(Long receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getReceiverUsername() {
        return receiverUsername;
    }

    public void setReceiverUsername(String receiverUsername) {
        this.receiverUsername = receiverUsername;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
