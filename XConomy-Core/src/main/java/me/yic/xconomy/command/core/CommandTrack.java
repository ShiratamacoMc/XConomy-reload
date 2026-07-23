/*
 *  This file (CommandTrack.java) is a part of project XConomy
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
package me.yic.xconomy.command.core;

import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CSender;
import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.data.tracking.TrackPageCache;
import me.yic.xconomy.data.tracking.TransactionCleanup;
import me.yic.xconomy.data.tracking.TransactionQuery;
import me.yic.xconomy.data.tracking.TransactionRecord;
import me.yic.xconomy.data.tracking.TransactionStatistics;
import me.yic.xconomy.lang.MessagesManager;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

public class CommandTrack extends CommandCore {

    private static final ThreadLocal<SimpleDateFormat> dateFormat =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("MM-dd HH:mm"));

    public static boolean onCommand(CSender sender, String[] args) {
        if (!XConomyLoad.isTransactionTrackingEnabled()) {
            sendMessages(sender, PREFIX + MessagesManager.systemMessage("<red>交易追踪功能未启用"));
            return true;
        }

        // /xconomy track income [page]
        // /xconomy track expense [page]
        // /xconomy track <player> income [page]
        // /xconomy track <player> expense [page]
        // /xconomy track cleanup [days]
        // /xconomy track flow <transactionId>

        if (args.length < 2) {
            sendUsage(sender, "usage_track");
            return true;
        }

        String subCommand = args[1].toLowerCase();

        // Check for cleanup command
        if (subCommand.equals("cleanup")) {
            if (!sender.isOp() && !sender.hasPermission("xconomy.admin.track.cleanup")) {
                sendMessages(sender, PREFIX + translateColorCodes("no_permission"));
                return true;
            }

            if (args.length > 3) {
                sendUsage(sender, "usage_track_cleanup");
                return true;
            }

            int days = XConomyLoad.Config.TRACKING_RETENTION_DAYS;
            if (args.length == 3) {
                Integer requestedDays = parsePositiveInteger(args[2]);
                if (requestedDays == null) {
                    sendUsage(sender, "usage_track_cleanup");
                    return true;
                }
                days = requestedDays;
            }

            if (days <= 0) {
                sendMessages(sender, PREFIX + MessagesManager.systemMessage("<red>无效的天数"));
                return true;
            }

            final int finalDays = days;
            AdapterManager.runTaskAsynchronously(() -> {
                int deleted = TransactionCleanup.cleanupOldRecords(finalDays);
                sendMessages(sender, PREFIX + translateColorCodes("track_cleanup_success")
                        .replace("%count%", String.valueOf(deleted)));
            });
            return true;
        }

        // Check if first arg is player name or income/expense
        UUID targetUUID;
        String playerName;
        String trackType;
        int page = 1;

        if (subCommand.equals("income") || subCommand.equals("expense")) {
            // /xconomy track income/expense [page]
            if (!sender.isPlayer()) {
                sendMessages(sender, PREFIX + MessagesManager.systemMessage("<red>控制台必须指定玩家名"));
                return true;
            }
            targetUUID = sender.toPlayer().getUniqueId();
            playerName = sender.getName();
            trackType = subCommand;
            if (args.length > 3) {
                sendUsage(sender, "usage_track_self");
                return true;
            }
            if (args.length == 3) {
                Integer requestedPage = parsePositiveInteger(args[2]);
                if (requestedPage == null) {
                    sendUsage(sender, "usage_track_self");
                    return true;
                }
                page = requestedPage;
            }
        } else {
            // /xconomy track <player> income/expense [page]
            if (!sender.isOp() && !sender.hasPermission("xconomy.admin.track.other")) {
                sendMessages(sender, PREFIX + translateColorCodes("track_view_other_no_permission"));
                return true;
            }

            PlayerData pd = DataCon.getPlayerData(args[1]);
            if (pd == null) {
                sendMessages(sender, PREFIX + translateColorCodes("no_account"));
                return true;
            }

            targetUUID = pd.getUniqueId();
            playerName = pd.getName();

            if (args.length < 3) {
                sendUsage(sender, "usage_track_other");
                return true;
            }

            trackType = args[2].toLowerCase();
            if (!trackType.equals("income") && !trackType.equals("expense")) {
                sendUsage(sender, "usage_track_other");
                return true;
            }

            if (args.length > 4) {
                sendUsage(sender, "usage_track_other");
                return true;
            }
            if (args.length == 4) {
                Integer requestedPage = parsePositiveInteger(args[3]);
                if (requestedPage == null) {
                    sendUsage(sender, "usage_track_other");
                    return true;
                }
                page = requestedPage;
            }
        }

        if (page < 1) {
            page = 1;
        }

        final UUID finalUUID = targetUUID;
        final String finalPlayerName = playerName;
        final String finalTrackType = trackType;
        final int finalPage = page;

        AdapterManager.runTaskAsynchronously(() -> {
            displayTransactionRecords(sender, finalUUID, finalPlayerName, finalTrackType, finalPage);
            // 刷新页数缓存，让下次 tab 补全拿到最新值
            TrackPageCache.refresh(finalUUID, finalTrackType);
        });

        return true;
    }

    private static void displayTransactionRecords(CSender sender, UUID targetUUID, String playerName, String trackType, int page) {
        int pageSize = XConomyLoad.Config.TRACKING_RECORDS_PER_PAGE;
        List<TransactionRecord> records;
        int totalRecords;

        if (trackType.equals("income")) {
            records = TransactionQuery.getIncomeTransactions(targetUUID, page, pageSize);
            totalRecords = TransactionQuery.countIncomeRecords(targetUUID);
        } else {
            records = TransactionQuery.getExpenseTransactions(targetUUID, page, pageSize);
            totalRecords = TransactionQuery.countExpenseRecords(targetUUID);
        }

        int maxPage = (int) Math.ceil((double) totalRecords / pageSize);
        if (maxPage == 0) maxPage = 1;

        // Send title
        String title = trackType.equals("income") ? 
                translateColorCodes("track_income_title") : 
                translateColorCodes("track_expense_title");
        sendMessages(sender, title.replace("%page%", page + "/" + maxPage));

        // Send statistics
        TransactionStatistics stats = TransactionQuery.getStatistics(targetUUID);
        sendMessages(sender, translateColorCodes("track_statistics")
                .replace("%income%", DataFormat.shown(stats.getTotalIncome()))
                .replace("%expense%", DataFormat.shown(stats.getTotalExpense()))
                .replace("%profit%", DataFormat.shown(stats.getNetProfit())));

        // Send records
        if (records.isEmpty()) {
            sendMessages(sender, PREFIX + translateColorCodes("track_no_records"));
        } else {
            for (TransactionRecord record : records) {
                String message = formatTransactionRecord(record, trackType);
                sendMessages(sender, message);
            }
        }
    }

    private static String formatTransactionRecord(TransactionRecord record, String trackType) {
        String template = trackType.equals("income") ?
                translateColorCodes("track_income_text") :
                translateColorCodes("track_expense_text");

        String time = dateFormat.get().format(record.getDatetime());
        String amount = DataFormat.shown(record.getAmount());
        String otherParty = "N/A";
        String typeBase = getTransactionTypeDisplay(record.getTransactionType(), record.getCommand());

        // 如果有原因（comment 字段），在类型后附加原因
        String comment = record.getComment();
        String type;
        if (comment != null && !comment.isEmpty()
                && !comment.equalsIgnoreCase("null")
                && !comment.equalsIgnoreCase("N/A")) {
            type = translateColorCodes("track_type_with_reason")
                    .replace("%type%", typeBase)
                    .replace("%reason%", comment);
        } else {
            type = typeBase;
        }

        if (trackType.equals("income")) {
            if (record.getFromUid() != null) {
                PlayerData fromPlayer = DataCon.getPlayerData(record.getFromUid());
                if (fromPlayer != null) {
                    otherParty = fromPlayer.getName();
                }
            } else {
                String txType = record.getTransactionType();
                if (txType != null && txType.startsWith("ADMIN_")) {
                    otherParty = extractSenderFromCommand(record.getCommand());
                } else {
                    otherParty = translateColorCodes("track_type_system");
                }
            }
        } else {
            if (record.getToUid() != null) {
                PlayerData toPlayer = DataCon.getPlayerData(record.getToUid());
                if (toPlayer != null) {
                    otherParty = toPlayer.getName();
                }
            } else {
                String txType = record.getTransactionType();
                if (txType != null && txType.startsWith("ADMIN_")) {
                    otherParty = extractSenderFromCommand(record.getCommand());
                } else {
                    otherParty = translateColorCodes("track_type_system");
                }
            }
        }

        return template
                .replace("%time%", time)
                .replace("%amount%", amount)
                .replace("%from%", otherParty)
                .replace("%to%", otherParty)
                .replace("%type%", type);
    }

    private static String getTransactionTypeDisplay(String transactionType, String command) {
        if (transactionType == null) {
            return translateColorCodes("track_type_unknown");
        }

        // Build plugin name label from the command field (plugin name stored there)
        // Falls back gracefully if command is null/empty
        String pluginLabel = (command != null && !command.isEmpty() && !command.equalsIgnoreCase("null"))
                ? command : null;

        switch (transactionType.toUpperCase()) {
            case "PAY_SEND":
                return translateColorCodes("track_type_pay_send");
            case "PAY_RECEIVE":
                return translateColorCodes("track_type_pay_receive");
            case "ADMIN_GIVE":
                return translateColorCodes("track_type_admin_give");
            case "ADMIN_TAKE":
                return translateColorCodes("track_type_admin_take");
            case "ADMIN_SET":
                return translateColorCodes("track_type_admin_set");
            case "PLUGIN_GIVE":
            case "PLUGIN_API_GIVE":
                return pluginLabel != null
                        ? translateColorCodes("track_type_plugin_give").replace("%plugin%", pluginLabel)
                        : translateColorCodes("track_type_plugin_give").replace("%plugin%", translateColorCodes("track_type_unknown"));
            case "PLUGIN_TAKE":
            case "PLUGIN_API_TAKE":
                return pluginLabel != null
                        ? translateColorCodes("track_type_plugin_take").replace("%plugin%", pluginLabel)
                        : translateColorCodes("track_type_plugin_take").replace("%plugin%", translateColorCodes("track_type_unknown"));
            case "PLUGIN_SET":
            case "PLUGIN_API_SET":
                return pluginLabel != null
                        ? translateColorCodes("track_type_plugin_set").replace("%plugin%", pluginLabel)
                        : translateColorCodes("track_type_plugin_set").replace("%plugin%", translateColorCodes("track_type_unknown"));
            default:
                // For truly unknown types, show the raw value so admins can diagnose
                return transactionType;
        }
    }

    /**
     * Extract the sender name from a command string formatted as "[SenderName] ..."
     * Returns the name inside the brackets, or a fallback label if not present.
     */
    private static String extractSenderFromCommand(String command) {
        if (command != null && command.startsWith("[")) {
            int end = command.indexOf(']');
            if (end > 1) {
                return command.substring(1, end);
            }
        }
        return translateColorCodes("track_type_admin");
    }

    private static void sendTrackHelp(CSender sender) {
        sendMessages(sender, translateColorCodes("help15"));
        if (sender.isOp() || sender.hasPermission("xconomy.admin.track.other")) {
            sendMessages(sender, translateColorCodes("help16"));
        }
        if (sender.isOp() || sender.hasPermission("xconomy.admin.track.cleanup")) {
            sendMessages(sender, translateColorCodes("help17"));
        }
    }
}

