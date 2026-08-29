package com.pstu.backend.dto.response;

import java.math.BigDecimal;

public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private String fullName;
    private BigDecimal balance;
    private String currency;

    public AuthResponse() {
    }

    public AuthResponse(String token, String username, String email, String fullName, BigDecimal balance, String currency) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.balance = balance;
        this.currency = currency;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
