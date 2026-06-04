/*
 *  This file (TransactionCleanup.java) is a part of project XConomy
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

import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.data.sql.SQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class TransactionCleanup {

    private static volatile boolean cleanupEnabled = false;

    /**
     * Schedule automatic cleanup task
     */
    public static void scheduleAutoCleanup() {
        if (!XConomyLoad.Config.TRACKING_ENABLE || !XConomyLoad.Config.TRACKING_AUTO_CLEANUP) {
            return;
        }

        if (cleanupEnabled) {
            return;
        }

        int retentionDays = XConomyLoad.Config.TRACKING_RETENTION_DAYS;
        if (retentionDays <= 0) {
            return;
        }

        cleanupEnabled = true;

        // Parse cleanup time (format: "03:00")
        String cleanupTime = XConomyLoad.Config.TRACKING_CLEANUP_TIME;
        long initialDelaySeconds = calculateInitialDelaySeconds(cleanupTime);

        scheduleNext(initialDelaySeconds, retentionDays);

        XConomy.getInstance().logger("Transaction auto cleanup scheduled at " + cleanupTime, 0, null);
    }

    /**
     * Schedule the next cleanup run. The task re-schedules itself for the
     * following day, emulating a daily repeating task using the
     * single-shot {@code runTaskLaterAsynchronously} API available on all platforms.
     *
     * @param delaySeconds  Delay before this run, in seconds
     * @param retentionDays Records older than this many days will be deleted
     */
    private static void scheduleNext(long delaySeconds, int retentionDays) {
        AdapterManager.runTaskLaterAsynchronously(() -> {
            if (!cleanupEnabled) {
                return;
            }

            int deleted = cleanupOldRecords(retentionDays);
            if (deleted > 0) {
                XConomy.getInstance().logger("Auto cleanup: Deleted " + deleted + " old transaction records", 0, null);
            }

            // Reschedule for the next day
            if (cleanupEnabled) {
                scheduleNext(TimeUnit.DAYS.toSeconds(1), retentionDays);
            }
        }, delaySeconds);
    }

    /**
     * Manually cleanup old records
     * @param days Records older than this many days will be deleted
     * @return Number of records deleted
     */
    public static int cleanupOldRecords(int days) {
        if (days <= 0) {
            return 0;
        }

        int deleted = 0;
        Connection connection = null;
        try {
            connection = SQL.database.getConnectionAndCheck();
            if (connection == null) {
                return 0;
            }
            String query = "DELETE FROM " + SQL.tableRecordName +
                          " WHERE datetime < DATE_SUB(NOW(), INTERVAL ? DAY)";

            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, days);
            deleted = statement.executeUpdate();
            statement.close();

            XConomy.getInstance().logger("Cleaned up " + deleted + " transaction records older than " + days + " days", 0, null);
        } catch (SQLException e) {
            XConomy.getInstance().logger("Error cleaning up old records", 1, null);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                SQL.database.closeHikariConnection(connection);
            }
        }

        return deleted;
    }

    /**
     * Calculate initial delay for scheduled task based on target time
     * @param timeString Time in format "HH:mm"
     * @return Delay in seconds
     */
    private static long calculateInitialDelaySeconds(String timeString) {
        try {
            String[] parts = timeString.split(":");
            int targetHour = Integer.parseInt(parts[0]);
            int targetMinute = Integer.parseInt(parts[1]);

            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar target = java.util.Calendar.getInstance();
            target.set(java.util.Calendar.HOUR_OF_DAY, targetHour);
            target.set(java.util.Calendar.MINUTE, targetMinute);
            target.set(java.util.Calendar.SECOND, 0);

            // If target time has passed today, schedule for tomorrow
            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }

            long delayMillis = target.getTimeInMillis() - now.getTimeInMillis();
            return delayMillis / 1000;
        } catch (Exception e) {
            XConomy.getInstance().logger("Invalid cleanup time format: " + timeString, 1, null);
            // Default to 24 hours from now
            return TimeUnit.HOURS.toSeconds(24);
        }
    }

    /**
     * Cancel scheduled cleanup (for plugin reload/disable)
     */
    public static void cancelScheduledCleanup() {
        cleanupEnabled = false;
    }
}

