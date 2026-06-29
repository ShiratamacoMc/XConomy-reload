/*
 *  This file (DataMigration.java) is a part of project XConomy
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
package me.yic.xconomy.data;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CConfig;
import me.yic.xconomy.data.sql.SQL;
import me.yic.xconomy.info.DataBaseConfig;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataMigration {
    
    /**
     * 从当前数据库迁移到目标数据库类型
     * @param targetType "SQLite" 或 "MySQL"
     * @param callback 迁移进度回调
     * @return 是否成功
     */
    public static boolean migrate(String targetType, MigrationCallback callback) {
        int currentType = XConomyLoad.DConfig.getStorageType();
        String currentTypeName = getTypeName(currentType);
        
        callback.onStart(currentTypeName, targetType);
        
        // 检查是否是相同类型
        if (currentTypeName.equalsIgnoreCase(targetType)) {
            callback.onError("源数据库和目标数据库类型相同");
            return false;
        }
        
        try {
            // 读取源数据库中的所有数据
            Map<UUID, PlayerBalance> sourceData = readAllData(currentType);
            
            if (sourceData.isEmpty()) {
                callback.onError("源数据库中没有数据");
                return false;
            }
            
            callback.onProgress("读取到 " + sourceData.size() + " 条数据");
            
            // 连接目标数据库
            Connection targetConn = connectTargetDatabase(targetType);
            if (targetConn == null) {
                callback.onError("无法连接到目标数据库，请检查 database.yml 配置");
                return false;
            }
            
            callback.onProgress("已连接到目标数据库");
            
            // 创建目标表
            createTargetTable(targetConn, targetType);
            callback.onProgress("已创建目标数据表");
            
            // 写入数据
            int successCount = writeData(targetConn, sourceData, callback);
            
            targetConn.close();
            
            callback.onComplete(successCount, sourceData.size());
            
            return successCount == sourceData.size();
            
        } catch (Exception e) {
            callback.onError("迁移过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private static String getTypeName(int type) {
        switch (type) {
            case 1: return "SQLite";
            case 2: return "MySQL";
            case 3: return "MariaDB";
            default: return "Unknown";
        }
    }
    
    private static Map<UUID, PlayerBalance> readAllData(int sourceType) throws SQLException {
        Map<UUID, PlayerBalance> data = new HashMap<>();
        
        String query = "SELECT UID, player, balance FROM " + SQL.tableName;
        
        try (Connection conn = SQL.database.getConnectionAndCheck();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String uuidStr = rs.getString("UID");
                String playerName = rs.getString("player");
                BigDecimal balance = rs.getBigDecimal("balance");
                
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    data.put(uuid, new PlayerBalance(playerName, balance));
                } catch (IllegalArgumentException e) {
                    XConomy.getInstance().logger(null, 1, "Invalid UUID: " + uuidStr);
                }
            }
            
            SQL.database.closeHikariConnection(conn);
        }
        
        return data;
    }
    
    private static DataBaseConfig createTargetConfig(String targetType) {
        // 使用当前配置文件，只是为了读取目标数据库配置
        File file = new File(XConomy.getInstance().getDataFolder(), "database.yml");
        if (!file.exists()) {
            return null;
        }
        
        CConfig config = new CConfig(file);
        DataBaseConfig targetConfig = new DataBaseConfig();
        DataBaseConfig.config = config;
        
        return targetConfig;
    }
    
    private static Connection connectTargetDatabase(String targetType) {
        try {
            DataBaseConfig config = XConomyLoad.DConfig;
            
            if (targetType.equalsIgnoreCase("SQLite")) {
                // 直接读取 SQLite 路径配置，而不是通过 gethost() 方法
                String path = config.config.getString("SQLite.path");
                if (path == null || path.equalsIgnoreCase("Default")) {
                    // 创建在插件数据目录
                    path = XConomy.getInstance().getDataFolder().getAbsolutePath() + File.separator + "playerdata_migrated.db";
                } else {
                    // 为迁移创建新文件名
                    File originalFile = new File(path);
                    String parent = originalFile.getParent();
                    String name = originalFile.getName().replace(".db", "_migrated.db");
                    
                    // 处理 parent 为 null 的情况（相对路径）
                    if (parent != null) {
                        path = parent + File.separator + name;
                    } else {
                        // 如果是相对路径，在插件目录下创建
                        path = XConomy.getInstance().getDataFolder().getAbsolutePath() + File.separator + name;
                    }
                }
                
                // 确保父目录存在
                File dbFile = new File(path);
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // 记录文件路径以便用户查找
                XConomy.getInstance().logger(null, 0, "Creating SQLite database at: " + path);
                
                Class.forName("org.sqlite.JDBC");
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
                // 设置自动提交
                conn.setAutoCommit(true);
                return conn;
                
            } else if (targetType.equalsIgnoreCase("MySQL") || targetType.equalsIgnoreCase("MariaDB")) {
                // 直接读取 MySQL 配置
                String host = config.config.getString("MySQL.host");
                int port = config.config.getInt("MySQL.port");
                String user = config.config.getString("MySQL.user");
                String pass = config.config.getString("MySQL.pass");
                String database = config.config.getString("MySQL.database");
                
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database 
                    + "?useSSL=" + config.config.getBoolean("MySQL.usessl")
                    + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                
                Class.forName("com.mysql.cj.jdbc.Driver");
                Connection conn = DriverManager.getConnection(url, user, pass);
                conn.setAutoCommit(true);
                return conn;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    private static void createTargetTable(Connection conn, String targetType) throws SQLException {
        String createTableSQL;
        
        if (targetType.equalsIgnoreCase("SQLite")) {
            // SQLite 使用 XConomy 标准格式
            createTableSQL = "CREATE TABLE IF NOT EXISTS " + SQL.tableName + " ("
                + "UID VARCHAR(50) NOT NULL PRIMARY KEY, "
                + "player VARCHAR(50) NOT NULL, "
                + "balance DOUBLE(20,2) NOT NULL, "
                + "hidden INT(5) NOT NULL DEFAULT 0"
                + ")";
        } else {
            // MySQL 使用 XConomy 标准格式
            createTableSQL = "CREATE TABLE IF NOT EXISTS " + SQL.tableName + " ("
                + "UID VARCHAR(50) NOT NULL PRIMARY KEY, "
                + "player VARCHAR(50) NOT NULL, "
                + "balance DOUBLE(20,2) NOT NULL, "
                + "hidden INT(5) NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
        }
    }
    
    private static int writeData(Connection conn, Map<UUID, PlayerBalance> data, MigrationCallback callback) {
        int successCount = 0;
        int totalCount = data.size();
        
        // 检测数据库类型
        boolean isSQLite = false;
        try {
            isSQLite = conn.getMetaData().getURL().startsWith("jdbc:sqlite:");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        String insertSQL;
        if (isSQLite) {
            // SQLite 使用 INSERT OR REPLACE 来覆盖已存在的数据
            insertSQL = "INSERT OR REPLACE INTO " + SQL.tableName + " (UID, player, balance, hidden) "
                + "VALUES (?, ?, ?, COALESCE((SELECT hidden FROM " + SQL.tableName + " WHERE UID = ?), 0))";
        } else {
            // MySQL 使用 ON DUPLICATE KEY UPDATE 来覆盖已存在的数据
            insertSQL = "INSERT INTO " + SQL.tableName + " (UID, player, balance, hidden) VALUES (?, ?, ?, 0) "
                + "ON DUPLICATE KEY UPDATE player=VALUES(player), balance=VALUES(balance)";
        }
        
        try {
            PreparedStatement pstmt = conn.prepareStatement(insertSQL);
            int batchCount = 0;
            
            for (Map.Entry<UUID, PlayerBalance> entry : data.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerBalance pb = entry.getValue();
                
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, pb.playerName);
                pstmt.setBigDecimal(3, pb.balance);
                
                // SQLite 需要额外的 UID 参数用于子查询
                if (isSQLite) {
                    pstmt.setString(4, uuid.toString());
                }
                
                pstmt.addBatch();
                batchCount++;
                
                // 每100条执行一次批量插入
                if (batchCount >= 100) {
                    pstmt.executeBatch();
                    successCount += batchCount;
                    callback.onProgress("已迁移 " + successCount + "/" + totalCount + " 条数据");
                    batchCount = 0;
                }
            }
            
            // 执行剩余的数据
            if (batchCount > 0) {
                pstmt.executeBatch();
                successCount += batchCount;
            }
            
            pstmt.close();
            
            // 确保数据被提交（虽然已设置autocommit，但保险起见）
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            
        } catch (SQLException e) {
            callback.onError("写入数据时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        return successCount;
    }
    
    /**
     * 玩家余额数据类
     */
    private static class PlayerBalance {
        String playerName;
        BigDecimal balance;
        
        PlayerBalance(String playerName, BigDecimal balance) {
            this.playerName = playerName;
            this.balance = balance;
        }
    }
    
    /**
     * 迁移进度回调接口
     */
    public interface MigrationCallback {
        void onStart(String sourceType, String targetType);
        void onProgress(String message);
        void onComplete(int successCount, int totalCount);
        void onError(String error);
    }
}
