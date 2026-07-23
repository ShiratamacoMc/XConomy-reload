package me.yic.xconomy.data;

import java.math.BigDecimal;

public final class BalanceTransferResult {
    private final BalanceMutationResult.Status status;
    private final BigDecimal senderBalance;
    private final BigDecimal targetBalance;
    private final Long senderTransactionId;
    private final Long targetTransactionId;

    private BalanceTransferResult(BalanceMutationResult.Status status, BigDecimal senderBalance,
                                  BigDecimal targetBalance, Long senderTransactionId,
                                  Long targetTransactionId) {
        this.status = status;
        this.senderBalance = senderBalance;
        this.targetBalance = targetBalance;
        this.senderTransactionId = senderTransactionId;
        this.targetTransactionId = targetTransactionId;
    }

    public static BalanceTransferResult success(BigDecimal senderBalance, BigDecimal targetBalance,
                                                Long senderTransactionId, Long targetTransactionId) {
        return new BalanceTransferResult(BalanceMutationResult.Status.SUCCESS, senderBalance,
                targetBalance, senderTransactionId, targetTransactionId);
    }

    public static BalanceTransferResult failure(BalanceMutationResult.Status status,
                                                BigDecimal senderBalance, BigDecimal targetBalance) {
        return new BalanceTransferResult(status, senderBalance, targetBalance, null, null);
    }

    public boolean isSuccess() {
        return status == BalanceMutationResult.Status.SUCCESS;
    }

    public BalanceMutationResult.Status getStatus() {
        return status;
    }

    public BigDecimal getSenderBalance() {
        return senderBalance;
    }

    public BigDecimal getTargetBalance() {
        return targetBalance;
    }

    public Long getSenderTransactionId() {
        return senderTransactionId;
    }

    public Long getTargetTransactionId() {
        return targetTransactionId;
    }
}
