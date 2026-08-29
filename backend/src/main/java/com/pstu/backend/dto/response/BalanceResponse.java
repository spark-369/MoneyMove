package com.pstu.backend.dto.response;

import java.math.BigDecimal;

public class BalanceResponse {
    private BigDecimal balance;
    private String currency;

    public BalanceResponse() {
    }

    public BalanceResponse(BigDecimal balance, String currency) {
        this.balance = balance;
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
