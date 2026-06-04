/*
 *  This file (TransactionQuery.java) is a part of project XConomy
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

import me.yic.xconomy.XConomy;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.sql.SQL;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionQuery {

    /**
     * Query income transactions for a player
     * @param player Player UUID
     * @param page Page number (starts from 1)
     * @param pageSize Records per page
     * @return List of transaction records
     */
    public static List<TransactionRecord> getIncomeTransactions(UUID player, int page, int pageSize) {
        List<TransactionRecord> records = new ArrayList<>();
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return records;
            }
            String query = "SELECT * FROM " + SQL.tableRecordName + 
                          " WHERE to_uid = ? OR (uid = ? AND operation = 'DEPOSIT') " +
                          " ORDER BY datetime DESC LIMIT ? OFFSET ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());
            statement.setInt(3, pageSize);
            statement.setInt(4, (page - 1) * pageSize);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                records.add(parseRecord(rs));
            }

            rs.close();
            statement.close();
        } catch (SQLException e) {
            XConomy.getInstance().logger("Error querying income transactions", 1, null);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }
        return records;
    }

    /**
     * Query expense transactions for a player
     * @param player Player UUID
     * @param page Page number (starts from 1)
     * @param pageSize Records per page
     * @return List of transaction records
     */
    public static List<TransactionRecord> getExpenseTransactions(UUID player, int page, int pageSize) {
        List<TransactionRecord> records = new ArrayList<>();
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return records;
            }
            String query = "SELECT * FROM " + SQL.tableRecordName + 
                          " WHERE from_uid = ? OR (uid = ? AND operation = 'WITHDRAW') " +
                          " ORDER BY datetime DESC LIMIT ? OFFSET ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());
            statement.setInt(3, pageSize);
            statement.setInt(4, (page - 1) * pageSize);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                records.add(parseRecord(rs));
            }

            rs.close();
            statement.close();
        } catch (SQLException e) {
            XConomy.getInstance().logger("Error querying expense transactions", 1, null);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }
        return records;
    }

    /**
     * Get transaction statistics for a player
     * @param player Player UUID
     * @return Transaction statistics
     */
    public static TransactionStatistics getStatistics(UUID player) {
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        int incomeCount = 0;
        int expenseCount = 0;

        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return new TransactionStatistics(totalIncome, totalExpense, incomeCount, expenseCount);
            }

            // Query income
            String incomeQuery = "SELECT COALESCE(SUM(amount), 0) as total, COUNT(*) as count FROM " + SQL.tableRecordName + 
                                " WHERE to_uid = ? OR (uid = ? AND operation = 'DEPOSIT')";
            PreparedStatement incomeStmt = connection.prepareStatement(incomeQuery);
            incomeStmt.setString(1, player.toString());
            incomeStmt.setString(2, player.toString());
            ResultSet incomeRs = incomeStmt.executeQuery();
            if (incomeRs.next()) {
                totalIncome = DataFormat.formatString(incomeRs.getString("total"));
                incomeCount = incomeRs.getInt("count");
            }
            incomeRs.close();
            incomeStmt.close();

            // Query expense
            String expenseQuery = "SELECT COALESCE(SUM(amount), 0) as total, COUNT(*) as count FROM " + SQL.tableRecordName + 
                                 " WHERE from_uid = ? OR (uid = ? AND operation = 'WITHDRAW')";
            PreparedStatement expenseStmt = connection.prepareStatement(expenseQuery);
            expenseStmt.setString(1, player.toString());
            expenseStmt.setString(2, player.toString());
            ResultSet expenseRs = expenseStmt.executeQuery();
            if (expenseRs.next()) {
                totalExpense = DataFormat.formatString(expenseRs.getString("total"));
                expenseCount = expenseRs.getInt("count");
            }
            expenseRs.close();
            expenseStmt.close();
        } catch (SQLException e) {
            XConomy.getInstance().logger("Error querying transaction statistics", 1, null);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }

        return new TransactionStatistics(totalIncome, totalExpense, incomeCount, expenseCount);
    }

    /**
     * Count total income records for a player
     */
    public static int countIncomeRecords(UUID player) {
        int count = 0;
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return 0;
            }
            String query = "SELECT COUNT(*) as count FROM " + SQL.tableRecordName + 
                          " WHERE to_uid = ? OR (uid = ? AND operation = 'DEPOSIT')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                count = rs.getInt("count");
            }

            rs.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }
        return count;
    }

    /**
     * Count total expense records for a player
     */
    public static int countExpenseRecords(UUID player) {
        int count = 0;
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return 0;
            }
            String query = "SELECT COUNT(*) as count FROM " + SQL.tableRecordName + 
                          " WHERE from_uid = ? OR (uid = ? AND operation = 'WITHDRAW')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                count = rs.getInt("count");
            }

            rs.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }
        return count;
    }

    /**
     * Parse ResultSet to TransactionRecord
     */
    private static TransactionRecord parseRecord(ResultSet rs) throws SQLException {
        UUID uid = null;
        UUID fromUid = null;
        UUID toUid = null;

        String uidStr = rs.getString("uid");
        if (uidStr != null && !uidStr.equals("N/A")) {
            try {
                uid = UUID.fromString(uidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        String fromUidStr = rs.getString("from_uid");
        if (fromUidStr != null && !fromUidStr.equals("N/A")) {
            try {
                fromUid = UUID.fromString(fromUidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        String toUidStr = rs.getString("to_uid");
        if (toUidStr != null && !toUidStr.equals("N/A")) {
            try {
                toUid = UUID.fromString(toUidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        String traceId = rs.getString("trace_id");
        Long parentId = rs.getLong("parent_transaction_id");
        if (rs.wasNull()) {
            parentId = null;
        }

        return new TransactionRecord(
            rs.getInt("id"),
            rs.getString("type"),
            uid,
            rs.getString("player"),
            DataFormat.formatString(rs.getString("balance")),
            DataFormat.formatString(rs.getString("amount")),
            rs.getString("operation"),
            rs.getString("command"),
            rs.getString("comment"),
            rs.getTimestamp("datetime"),
            fromUid,
            toUid,
            rs.getString("transaction_type"),
            rs.getString("server_id"),
            traceId,
            parentId
        );
    }
}

