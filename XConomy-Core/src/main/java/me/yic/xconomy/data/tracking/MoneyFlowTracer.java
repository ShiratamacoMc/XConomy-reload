/*
 *  This file (MoneyFlowTracer.java) is a part of project XConomy
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
import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.sql.SQL;
import me.yic.xconomy.data.syncdata.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 资金流追踪器 - 追踪一笔钱的完整流转链路
 */
public class MoneyFlowTracer {

    /**
     * 追踪一笔交易的完整流转链路
     * @param transactionId 交易记录ID
     * @return 资金流转链路
     */
    public static MoneyFlowChain traceMoneyFlow(long transactionId) {
        MoneyFlowChain chain = new MoneyFlowChain();
        
        try {
            // 1. 获取起始交易
            TransactionRecord startRecord = getTransactionById(transactionId);
            if (startRecord == null) {
                return null;
            }

            // 2. 向上追溯到源头
            List<TransactionRecord> upstreamChain = traceUpstream(transactionId);
            chain.setUpstreamChain(upstreamChain);

            // 3. 向下追踪到最终去向
            List<TransactionRecord> downstreamChain = traceDownstream(transactionId);
            chain.setDownstreamChain(downstreamChain);

            // 4. 设置当前交易
            chain.setCurrentTransaction(startRecord);

            // 5. 计算资金当前位置
            if (!downstreamChain.isEmpty()) {
                TransactionRecord lastRecord = downstreamChain.get(downstreamChain.size() - 1);
                chain.setCurrentHolder(lastRecord.getToUid());
            } else if (startRecord.getToUid() != null) {
                chain.setCurrentHolder(startRecord.getToUid());
            }

        } catch (Exception e) {
            XConomy.getInstance().logger("Error tracing money flow", 1, null);
            e.printStackTrace();
        }

        return chain;
    }

    /**
     * 根据追踪ID查询所有相关交易
     * @param traceId 追踪ID
     * @return 交易列表
     */
    public static List<TransactionRecord> getTransactionsByTraceId(String traceId) {
        List<TransactionRecord> records = new ArrayList<>();

        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return records;
            }
            String query = "SELECT * FROM " + SQL.tableRecordName +
                          " WHERE trace_id = ? ORDER BY datetime ASC";

            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, traceId);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                records.add(parseRecord(rs));
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

        return records;
    }

    /**
     * 查找某笔资金的所有下游交易（这笔钱被谁花了）
     * @param transactionId 交易ID
     * @return 下游交易列表
     */
    private static List<TransactionRecord> traceDownstream(long transactionId) {
        List<TransactionRecord> downstream = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        
        queue.offer(transactionId);
        visited.add(transactionId);

        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return downstream;
            }

            while (!queue.isEmpty()) {
                Long currentId = queue.poll();
                
                // 查找所有以当前交易为父交易的记录
                String query = "SELECT * FROM " + SQL.tableRecordName + 
                              " WHERE parent_transaction_id = ? ORDER BY datetime ASC";
                
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setLong(1, currentId);

                ResultSet rs = statement.executeQuery();
                while (rs.next()) {
                    TransactionRecord record = parseRecord(rs);

                    long childId = record.getId();
                    if (!visited.contains(childId)) {
                        visited.add(childId);
                        downstream.add(record);
                        queue.offer(childId);
                    }
                }

                rs.close();
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }

        return downstream;
    }

    /**
     * 向上追溯到资金源头
     * @param transactionId 交易ID
     * @return 上游交易链
     */
    private static List<TransactionRecord> traceUpstream(long transactionId) {
        List<TransactionRecord> upstream = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(transactionId);

        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return upstream;
            }
            Long currentId = transactionId;

            while (currentId != null) {
                TransactionRecord record = getTransactionById(currentId, connection);
                if (record == null || record.getParentTransactionId() == null) {
                    break;
                }

                Long parentId = record.getParentTransactionId();
                // Guard against cycles (self-reference or loops in parent chain)
                if (!visited.add(parentId)) {
                    break;
                }

                TransactionRecord parentRecord = getTransactionById(parentId, connection);
                if (parentRecord != null) {
                    upstream.add(0, parentRecord); // 添加到开头
                    currentId = (long) parentRecord.getId();
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }

        return upstream;
    }

    /**
     * 根据ID获取交易记录
     */
    private static TransactionRecord getTransactionById(long id) {
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return null;
            }
            return getTransactionById(id, connection);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }
    }

    /**
     * 根据ID获取交易记录（复用连接）
     */
    private static TransactionRecord getTransactionById(long id, Connection connection) {
        try {
            String query = "SELECT * FROM " + SQL.tableRecordName + " WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setLong(1, id);

            ResultSet rs = statement.executeQuery();
            TransactionRecord record = null;
            if (rs.next()) {
                record = parseRecord(rs);
            }

            rs.close();
            statement.close();
            return record;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解析 ResultSet 到 TransactionRecord
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

    /**
     * 资金流转链路类
     */
    public static class MoneyFlowChain {
        private List<TransactionRecord> upstreamChain = new ArrayList<>();
        private TransactionRecord currentTransaction;
        private List<TransactionRecord> downstreamChain = new ArrayList<>();
        private UUID currentHolder;

        public List<TransactionRecord> getUpstreamChain() {
            return upstreamChain;
        }

        public void setUpstreamChain(List<TransactionRecord> upstreamChain) {
            this.upstreamChain = upstreamChain;
        }

        public TransactionRecord getCurrentTransaction() {
            return currentTransaction;
        }

        public void setCurrentTransaction(TransactionRecord currentTransaction) {
            this.currentTransaction = currentTransaction;
        }

        public List<TransactionRecord> getDownstreamChain() {
            return downstreamChain;
        }

        public void setDownstreamChain(List<TransactionRecord> downstreamChain) {
            this.downstreamChain = downstreamChain;
        }

        public UUID getCurrentHolder() {
            return currentHolder;
        }

        public void setCurrentHolder(UUID currentHolder) {
            this.currentHolder = currentHolder;
        }

        public String getCurrentHolderName() {
            if (currentHolder == null) {
                return "已消耗/系统";
            }
            PlayerData pd = DataCon.getPlayerData(currentHolder);
            return pd != null ? pd.getName() : currentHolder.toString();
        }

        public List<TransactionRecord> getFullChain() {
            List<TransactionRecord> fullChain = new ArrayList<>();
            fullChain.addAll(upstreamChain);
            if (currentTransaction != null) {
                fullChain.add(currentTransaction);
            }
            fullChain.addAll(downstreamChain);
            return fullChain;
        }

        public int getTotalHops() {
            return upstreamChain.size() + downstreamChain.size() + (currentTransaction != null ? 1 : 0);
        }
    }
}

