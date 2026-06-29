/*
 *  This file (CommandBalance.java) is a part of project XConomy
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
import me.yic.xconomy.adapter.comp.CPlayer;
import me.yic.xconomy.adapter.comp.CSender;
import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.info.MessageConfig;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.utils.UUIDMode;

import java.math.BigDecimal;
import java.util.UUID;

public class CommandBalance extends CommandCore{

    /**
     * 从原始 args 中提取 flag 参数，返回去除 flag 后的纯位置参数列表。
     * 支持的 flag：
     *   -s  静默给予（不给目标玩家发消息）
     *   -q  后台静默（执行者和目标都不发消息，适合脚本）
     *   -r <原因...>  写入原因（-r 之后的所有词拼成原因）
     */
    private static class ParsedArgs {
        final String[] positional; // 去掉 flag 后的位置参数
        final boolean silent;      // -s
        final boolean quiet;       // -q
        final String reason;       // -r 后面的文字，null 表示未指定

        ParsedArgs(String[] positional, boolean silent, boolean quiet, String reason) {
            this.positional = positional;
            this.silent = silent;
            this.quiet = quiet;
            this.reason = reason;
        }
    }

    static ParsedArgs parseArgs(String[] args) {
        java.util.List<String> pos = new java.util.ArrayList<>();
        boolean silent = false;
        boolean quiet  = false;
        StringBuilder reason = null;

        // 已知的 flag 集合，用于判断 -r 的终止边界
        java.util.Set<String> KNOWN_FLAGS = new java.util.HashSet<>(
                java.util.Arrays.asList("-s", "-q", "-r"));

        for (int i = 0; i < args.length; i++) {
            String tok = args[i].toLowerCase();
            switch (tok) {
                case "-s":
                    silent = true;
                    break;
                case "-q":
                    quiet = true;
                    break;
                case "-r":
                    // 消耗后续词直到遇到下一个已知 flag 或末尾
                    reason = new StringBuilder();
                    for (int j = i + 1; j < args.length; j++) {
                        if (KNOWN_FLAGS.contains(args[j].toLowerCase())) {
                            i = j - 1; // 回退，让外层循环处理这个 flag
                            break;
                        }
                        if (reason.length() > 0) reason.append(" ");
                        reason.append(args[j]);
                        i = j; // 推进外层索引
                    }
                    break;
                default:
                    pos.add(args[i]);
            }
        }

        return new ParsedArgs(
                pos.toArray(new String[0]),
                silent,
                quiet,
                reason != null ? reason.toString().trim() : null
        );
    }

    public static boolean onCommand(CSender sender, String commandName, String[] rawArgs) {
        // 先解析 flag，得到纯位置参数
        ParsedArgs parsed = parseArgs(rawArgs);
        String[] args = parsed.positional;

        // 判断命令类型和长度
        int argsLength = args.length;
        
        // 特殊处理：检测是否是批量操作 (使用 * 通配符)
        boolean isBatchOperation = false;
        if (argsLength >= 4 && args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("give") || subCommand.equals("take")) && args[1].equals("*")) {
                isBatchOperation = true;
            }
        }
        
        // 处理原因消息
        StringBuilder reasonmessages = null;
        if (sender.isOp() | sender.hasPermission("xconomy.admin.give")
                | sender.hasPermission("xconomy.admin.take") | sender.hasPermission("xconomy.admin.set")) {
            // 优先使用 -r flag 指定的原因
            if (parsed.reason != null) {
                reasonmessages = new StringBuilder(parsed.reason);
            }
        }

        // 根据参数长度和类型决定处理方式
        if (argsLength == 0) {
            // /money - 查询自己余额
            return handleSelfBalance(sender);
            
        } else if (argsLength == 1) {
            // /money <玩家> - 查询玩家余额
            return handleOtherBalance(sender, args[0]);
            
        } else if (argsLength >= 3 && isBatchOperation) {
            // /money give * <all|online> <金额> [原因] - 批量操作
            return handleBatchOperation(sender, commandName, args, parsed, reasonmessages);
            
        } else if (argsLength >= 3) {
            // /money give <玩家> <金额> [flags] - 单个玩家操作
            return handleSinglePlayerOperation(sender, commandName, args, parsed, reasonmessages);
            
        } else {
            sendHelpMessage(sender, 1);
            return true;
        }
    }

    /**
     * 处理查询自己余额: /money
     */
    private static boolean handleSelfBalance(CSender sender) {
        if (!sender.isPlayer()) {
            sendMessages(sender, PREFIX + MessagesManager.systemMessage("§6控制台无法使用该指令"));
            return true;
        }

        if (!(sender.isOp() || sender.hasPermission("xconomy.user.balance"))) {
            sendMessages(sender, PREFIX + translateColorCodes("no_permission"));
            return true;
        }

        CPlayer player = sender.toPlayer();
        BigDecimal balance = DataCon.getPlayerData(player.getUniqueId()).getBalance();
        sendMessages(sender, PREFIX + translateColorCodes("balance")
                .replace("%balance%", DataFormat.shown(balance)));
        return true;
    }

    /**
     * 处理查询其他玩家余额: /money <玩家>
     */
    private static boolean handleOtherBalance(CSender sender, String targetName) {
        if (!(sender.isOp() || sender.hasPermission("xconomy.user.balance.other"))) {
            sendMessages(sender, PREFIX + translateColorCodes("no_permission"));
            return true;
        }

        PlayerData pd = DataCon.getPlayerData(targetName);
        if (pd == null) {
            sendMessages(sender, PREFIX + translateColorCodes(MessageConfig.NO_ACCOUNT));
            return true;
        }

        BigDecimal targetBalance = pd.getBalance();
        sendMessages(sender, PREFIX + translateColorCodes("balance_other")
                .replace("%player%", pd.getName())
                .replace("%balance%", DataFormat.shown(targetBalance)));
        return true;
    }

    /**
     * 处理单个玩家操作: /money give <玩家> <金额> [flags]
     */
    private static boolean handleSinglePlayerOperation(CSender sender, String commandName, 
                                                       String[] args, ParsedArgs parsed, 
                                                       StringBuilder reasonmessages) {
        if (!(sender.isOp() | sender.hasPermission("xconomy.admin.give")
                | sender.hasPermission("xconomy.admin.take") | sender.hasPermission("xconomy.admin.set"))) {
            sendHelpMessage(sender, 1);
            return true;
        }

        if (check()) {
            sendMessages(sender, PREFIX + MessagesManager.systemMessage("§cBC模式开启的情况下,无法在无人的服务器中使用OP命令"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String targetName = args[1];
        String amountStr = args[2];

        if (!isDouble(amountStr)) {
            sendMessages(sender, PREFIX + translateColorCodes("invalid_amount"));
            return true;
        }

        BigDecimal amount = DataFormat.formatString(amountStr);
        String amountFormatted = DataFormat.shown(amount);

        // 获取玩家数据
        PlayerData pd = DataCon.getPlayerData(targetName);
        if (pd == null) {
            if (targetName.length() > 25) {
                try {
                    UUID uuid = UUID.fromString(targetName);
                    pd = DataCon.getPlayerData(uuid);
                } catch (Exception ignored) {
                }
            }
            if (pd == null) {
                sendMessages(sender, PREFIX + translateColorCodes(MessageConfig.NO_ACCOUNT));
                return true;
            }
        }

        CPlayer target = AdapterManager.PLUGIN.getplayer(pd);
        UUID targetUUID = pd.getUniqueId();
        String realname = pd.getName();

        String senderName = sender.isPlayer() ? sender.getName() : translateColorCodes("console_name");
        String com = "[" + senderName + "] " + commandName + " " + subCommand + " " + realname + " " + amount;
        if (reasonmessages != null) {
            com += " " + reasonmessages;
        }

        switch (subCommand) {
            case "give": {
                if (!(sender.isOp() | sender.hasPermission("xconomy.admin.give"))) {
                    sendHelpMessage(sender, 1);
                    return true;
                }

                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sendMessages(sender, PREFIX + translateColorCodes("invalid_amount"));
                    return true;
                }

                BigDecimal bal = pd.getBalance();
                if (DataFormat.isMAX(bal.add(amount))) {
                    sendMessages(sender, PREFIX + translateColorCodes("over_maxnumber"));
                    if (target != null) {
                        sendMessages(target, PREFIX + translateColorCodes("over_maxnumber_receive"));
                    }
                    return true;
                }

                BigDecimal newbalance = DataCon.changeplayerdata("ADMIN_COMMAND", targetUUID, amount, true, com, reasonmessages);

                if (!parsed.quiet) {
                    sendMessages(sender, PREFIX + translateColorCodes("money_give")
                            .replace("%player%", realname)
                            .replace("%amount%", amountFormatted));
                }

                if (!parsed.silent && !parsed.quiet) {
                    String message;
                    if (reasonmessages != null && reasonmessages.length() > 0) {
                        message = PREFIX + reasonmessages;
                    } else if (checkMessage("money_give_receive")) {
                        message = PREFIX + translateColorCodes("money_give_receive")
                                .replace("%player%", realname)
                                .replace("%amount%", amountFormatted)
                                .replace("%balance%", DataFormat.shown(newbalance));
                    } else {
                        break;
                    }

                    if (!target.isOnline()) {
                        broadcastSendMessage(false, pd, message);
                    } else {
                        target.sendMessage(message);
                    }
                }
                break;
            }

            case "take": {
                if (!(sender.isOp() | sender.hasPermission("xconomy.admin.take"))) {
                    sendHelpMessage(sender, 1);
                    return true;
                }

                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sendMessages(sender, PREFIX + translateColorCodes("invalid_amount"));
                    return true;
                }

                BigDecimal bal = pd.getBalance();
                if (bal.compareTo(amount) < 0) {
                    sendMessages(sender, PREFIX + translateColorCodes("money_take_fail")
                            .replace("%player%", realname)
                            .replace("%amount%", amountFormatted));
                    return true;
                }

                BigDecimal newbalance = DataCon.changeplayerdata("ADMIN_COMMAND", targetUUID, amount, false, com, reasonmessages);

                if (!parsed.quiet) {
                    sendMessages(sender, PREFIX + translateColorCodes("money_take")
                            .replace("%player%", realname)
                            .replace("%amount%", amountFormatted));
                }

                if (!parsed.silent && !parsed.quiet) {
                    String mess;
                    if (reasonmessages != null && reasonmessages.length() > 0) {
                        mess = PREFIX + reasonmessages;
                    } else if (checkMessage("money_take_receive")) {
                        mess = PREFIX + translateColorCodes("money_take_receive")
                                .replace("%player%", realname)
                                .replace("%amount%", amountFormatted)
                                .replace("%balance%", DataFormat.shown(newbalance));
                    } else {
                        break;
                    }

                    if (!target.isOnline()) {
                        broadcastSendMessage(false, pd, mess);
                    } else {
                        target.sendMessage(mess);
                    }
                }
                break;
            }

            case "set": {
                if (!(sender.isOp() | sender.hasPermission("xconomy.admin.set"))) {
                    sendHelpMessage(sender, 1);
                    return true;
                }

                BigDecimal newbalance = DataCon.changeplayerdata("ADMIN_COMMAND", targetUUID, amount, null, com, reasonmessages);

                if (!parsed.quiet) {
                    sendMessages(sender, PREFIX + translateColorCodes("money_set")
                            .replace("%player%", realname)
                            .replace("%amount%", amountFormatted));
                }

                if (!parsed.silent && !parsed.quiet) {
                    String mess;
                    if (reasonmessages != null && reasonmessages.length() > 0) {
                        mess = PREFIX + reasonmessages;
                    } else if (checkMessage("money_set_receive")) {
                        mess = PREFIX + translateColorCodes("money_set_receive")
                                .replace("%player%", realname)
                                .replace("%amount%", amountFormatted)
                                .replace("%balance%", DataFormat.shown(newbalance));
                    } else {
                        break;
                    }

                    if (!target.isOnline()) {
                        broadcastSendMessage(false, pd, mess);
                    } else {
                        target.sendMessage(mess);
                    }
                }
                break;
            }

            default: {
                sendHelpMessage(sender, 1);
                break;
            }
        }
        return true;
    }

    /**
     * 处理批量操作: /money give * <all|online> <金额> [原因]
     */
    private static boolean handleBatchOperation(CSender sender, String commandName, 
                                                String[] args, ParsedArgs parsed, 
                                                StringBuilder reasonmessages) {
        if (!(sender.isOp() | sender.hasPermission("xconomy.admin.give")
                | sender.hasPermission("xconomy.admin.take"))) {
            sendHelpMessage(sender, 1);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String scope = args[2]; // "all" or "online"
        
        if (args.length < 4) {
            sendHelpMessage(sender, 1);
            return true;
        }

        if (!(scope.equalsIgnoreCase("all") | scope.equalsIgnoreCase("online"))) {
            sendHelpMessage(sender, 1);
            return true;
        }

        if (XConomyLoad.Config.UUIDMODE.equals(UUIDMode.SEMIONLINE) && scope.equalsIgnoreCase("online")) {
            sendMessages(sender, PREFIX + MessagesManager.systemMessage("§c该指令不支持在半正版模式中使用"));
            return true;
        }

        if (check()) {
            sendMessages(sender, PREFIX + MessagesManager.systemMessage("§cBC模式开启的情况下,无法在无人的服务器中使用OP命令"));
            return true;
        }

        String amountStr = args[3];
        if (!isDouble(amountStr)) {
            sendMessages(sender, PREFIX + translateColorCodes("invalid_amount"));
            return true;
        }

        BigDecimal amount = DataFormat.formatString(amountStr);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sendMessages(sender, PREFIX + translateColorCodes("invalid_amount"));
            return true;
        }

        String amountFormatted = DataFormat.shown(amount);
        String target = scope.equalsIgnoreCase("online") ? "OnlinePlayer" : "AllPlayer";

        // 构建原因：优先使用 -r flag，其次使用位置参数
        if (reasonmessages == null && args.length >= 5) {
            reasonmessages = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (i > 4) reasonmessages.append(" ");
                reasonmessages.append(args[i]);
            }
        }

        String senderName = sender.isPlayer() ? sender.getName() : translateColorCodes("console_name");
        String com = "[" + senderName + "] " + commandName + " " + subCommand + " * " + scope + " " + amount;
        if (reasonmessages != null) {
            com += " " + reasonmessages;
        }

        switch (subCommand) {
            case "give": {
                if (!(sender.isOp() | sender.hasPermission("xconomy.admin.give"))) {
                    sendHelpMessage(sender, 1);
                    return true;
                }

                DataCon.changeallplayerdata(scope, "ADMIN_COMMAND", amount, true, com, reasonmessages);
                sendMessages(sender, PREFIX + translateColorCodes("money_give")
                        .replace("%player%", target)
                        .replace("%amount%", amountFormatted));

                if (reasonmessages != null && reasonmessages.length() > 0) {
                    String message = PREFIX + reasonmessages;
                    AdapterManager.PLUGIN.broadcastMessage(message);
                    broadcastSendMessage(true, null, message);
                }
                break;
            }

            case "take": {
                if (!(sender.isOp() | sender.hasPermission("xconomy.admin.take"))) {
                    sendHelpMessage(sender, 1);
                    return true;
                }

                DataCon.changeallplayerdata(scope, "ADMIN_COMMAND", amount, false, com, reasonmessages);
                sendMessages(sender, PREFIX + translateColorCodes("money_take")
                        .replace("%player%", target)
                        .replace("%amount%", amountFormatted));

                if (reasonmessages != null && reasonmessages.length() > 0) {
                    String message = PREFIX + reasonmessages;
                    AdapterManager.PLUGIN.broadcastMessage(message);
                    broadcastSendMessage(true, null, message);
                }
                break;
            }

            default: {
                sendHelpMessage(sender, 1);
                break;
            }
        }

        return true;
    }

}
