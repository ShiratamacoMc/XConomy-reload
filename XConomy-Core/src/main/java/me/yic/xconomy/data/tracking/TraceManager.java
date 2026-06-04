/*
 *  This file (TraceManager.java) is a part of project XConomy
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

import me.yic.xconomy.data.sql.SQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪ID管理器 - 管理资金流转的追踪链
 */
public class TraceManager {
    
    // 玩家最近一次收入的交易ID缓存
    private static final Map<UUID, Long> lastIncomeTransactionCache = new ConcurrentHashMap<>();
    
    /**
     * 生成新的追踪ID
     * @return 追踪ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 记录玩家最近一次收入的交易ID
     * @param playerUUID 玩家UUID
     * @param transactionId 交易ID
     */
    public static void recordLastIncome(UUID playerUUID, Long transactionId) {
        if (playerUUID != null && transactionId != null) {
            lastIncomeTransactionCache.put(playerUUID, transactionId);
        }
    }
    
    /**
     * 获取玩家最近一次收入的交易ID。
     * 优先读取内存缓存；缓存缺失时（例如服务器重启后）回退到数据库查询，
     * 以保证追踪链在重启后仍然连续。
     * @param playerUUID 玩家UUID
     * @return 交易ID，如果没有则返回null
     */
    public static Long getLastIncome(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        Long cached = lastIncomeTransactionCache.get(playerUUID);
        if (cached != null) {
            return cached;
        }

        Long fromDb = queryLastIncomeFromDB(playerUUID);
        if (fromDb != null) {
            lastIncomeTransactionCache.put(playerUUID, fromDb);
        }
        return fromDb;
    }

    /**
     * 从数据库查询玩家最近一次收入交易的ID
     */
    private static Long queryLastIncomeFromDB(UUID playerUUID) {
        Long id = null;
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return null;
            }
            String query = "SELECT id FROM " + SQL.tableRecordName +
                          " WHERE to_uid = ? OR (uid = ? AND operation = 'DEPOSIT')" +
                          " ORDER BY datetime DESC, id DESC LIMIT 1";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, playerUUID.toString());
            statement.setString(2, playerUUID.toString());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                id = rs.getLong("id");
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
        return id;
    }
    
    /**
     * 清除玩家的收入记录缓存
     * @param playerUUID 玩家UUID
     */
    public static void clearLastIncome(UUID playerUUID) {
        lastIncomeTransactionCache.remove(playerUUID);
    }
    
    /**
     * 清除所有缓存
     */
    public static void clearAll() {
        lastIncomeTransactionCache.clear();
    }
}

