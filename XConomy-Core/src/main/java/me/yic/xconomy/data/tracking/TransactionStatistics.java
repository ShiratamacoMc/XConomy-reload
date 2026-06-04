/*
 *  This file (TransactionStatistics.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.data.tracking;

import java.math.BigDecimal;

public class TransactionStatistics {
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpense;
    private final int incomeCount;
    private final int expenseCount;

    public TransactionStatistics(BigDecimal totalIncome, BigDecimal totalExpense, int incomeCount, int expenseCount) {
        this.totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        this.totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;
        this.incomeCount = incomeCount;
        this.expenseCount = expenseCount;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public BigDecimal getNetProfit() {
        return totalIncome.subtract(totalExpense);
    }

    public int getIncomeCount() {
        return incomeCount;
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public int getTotalCount() {
        return incomeCount + expenseCount;
    }
}

