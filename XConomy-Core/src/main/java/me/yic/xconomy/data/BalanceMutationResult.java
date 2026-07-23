package me.yic.xconomy.data;

import java.math.BigDecimal;

public final class BalanceMutationResult {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        INSUFFICIENT_FUNDS,
        MAX_BALANCE,
        DATABASE_ERROR
    }

    private final Status status;
    private final BigDecimal balance;
    private final Long transactionId;

    private BalanceMutationResult(Status status, BigDecimal balance, Long transactionId) {
        this.status = status;
        this.balance = balance;
        this.transactionId = transactionId;
    }

    public static BalanceMutationResult success(BigDecimal balance, Long transactionId) {
        return new BalanceMutationResult(Status.SUCCESS, balance, transactionId);
    }

    public static BalanceMutationResult failure(Status status, BigDecimal balance) {
        return new BalanceMutationResult(status, balance, null);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getTransactionId() {
        return transactionId;
    }
}
