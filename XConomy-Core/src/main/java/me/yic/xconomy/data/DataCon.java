/*
 *  This file (DataCon.java) is a part of project XConomy
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

import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CPlayer;
import me.yic.xconomy.adapter.comp.CallAPI;
import me.yic.xconomy.data.caches.Cache;
import me.yic.xconomy.data.caches.CacheNonPlayer;
import me.yic.xconomy.data.redis.RedisPublisher;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.data.syncdata.SyncBalanceAll;
import me.yic.xconomy.data.syncdata.SyncData;
import me.yic.xconomy.data.syncdata.SyncDelData;
import me.yic.xconomy.data.tracking.TraceManager;
import me.yic.xconomy.info.MessageConfig;
import me.yic.xconomy.info.RecordInfo;
import me.yic.xconomy.info.SyncChannalType;
import me.yic.xconomy.utils.SendPluginMessage;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DataCon {
    private static final Object[] ACCOUNT_LOCKS = new Object[256];

    static {
        for (int i = 0; i < ACCOUNT_LOCKS.length; i++) {
            ACCOUNT_LOCKS[i] = new Object();
        }
    }

    public static PlayerData getPlayerData(UUID uuid) {
        return getPlayerDatai(uuid);
    }

    public static PlayerData getPlayerData(String username) {
        return getPlayerDatai(username);
    }

    public static BigDecimal getAccountBalance(String account) {
        BigDecimal bal = null;
        if (XConomyLoad.Config.DISABLE_CACHE){
            return DataLink.getBalNonPlayer(account);
        }
        if (CacheNonPlayer.CacheContainsKey(account)) {
            bal = CacheNonPlayer.getBalanceFromCacheOrDB(account);
        }
        if (bal == null){
            bal =  DataLink.getBalNonPlayer(account);
        }
        return bal;
    }

    private static <T> PlayerData getPlayerDatai(T u) {
        PlayerData pd = null;

        if (XConomyLoad.Config.DISABLE_CACHE) {
            return DataLink.getPlayerData(u);
        }

        if (Cache.CacheContainsKey(u)) {
            pd = Cache.getDataFromCache(u);
        }
        if (pd == null){
            pd = DataLink.getPlayerData(u);
        }
        return pd;
    }

    public static int getPlayerHiddenState(UUID uid) {
        if (Cache.phids.containsKey(uid)){
            return Cache.phids.get(uid);
        }
        if (DataLink.getPlayerData(uid) != null) {
            return Cache.phids.get(uid);
        }
        return 1;
    }
    public static void removePlayerHiddenState(UUID uid) {
        Cache.phids.remove(uid);
    }

    public static void deletePlayerData(PlayerData pd) {
        DataLink.deletePlayerData(pd.getUniqueId());
        Cache.removefromCache(pd.getUniqueId());

        if (!(pd instanceof SyncDelData) && XConomyLoad.getSyncData_Enable()) {
            SendMessTask(new SyncDelData(pd));
        }

        CPlayer cp = AdapterManager.PLUGIN.getplayer(pd);
        if(cp.isOnline()){
            cp.kickPlayer("[XConomy] " + AdapterManager.translateColorCodes(MessageConfig.DELETE_DATA));
        }
    }

    public static boolean hasaccountdatacache(String name) {
        return CacheNonPlayer.CacheContainsKey(name);
    }

    public static void deletedatafromcache(UUID u) {
        Cache.deleteDataFromCache(u);
    }

    public static boolean containinfieldslist(String name) {
        if (XConomyLoad.Config.NON_PLAYER_ACCOUNT_SUBSTRING != null) {
            for (String field : XConomyLoad.Config.NON_PLAYER_ACCOUNT_SUBSTRING) {
                if (name.contains(field)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static BigDecimal changeplayerdata(final String type, final UUID uid, final BigDecimal amount, final Boolean isAdd, final String command, final Object comment) {
        BalanceMutationResult result = changePlayerData(type, uid, amount, isAdd, command, comment);
        return result.getBalance();
    }

    public static BalanceMutationResult changePlayerData(final String type, final UUID uid,
                                                         final BigDecimal amount, final Boolean isAdd,
                                                         final String command, final Object comment) {
        synchronized (getAccountLock(uid)) {
            PlayerData pd = getPlayerData(uid);
            if (pd == null) {
                return BalanceMutationResult.failure(BalanceMutationResult.Status.NOT_FOUND, BigDecimal.ZERO);
            }

            UUID accountUid = pd.getUniqueId();
            BigDecimal oldBalance = pd.getBalance();
            RecordInfo recordInfo = createRecordInfo(type, accountUid, isAdd, command, comment);
            CallAPI.CallPlayerAccountEvent(accountUid, pd.getName(), oldBalance, amount, isAdd, recordInfo);

            BalanceMutationResult result = executeMutation(
                    () -> DataLink.save(pd, isAdd, amount, recordInfo),
                    BalanceMutationResult.failure(BalanceMutationResult.Status.DATABASE_ERROR, oldBalance));

            if (!result.isSuccess()) {
                if (result.getStatus() != BalanceMutationResult.Status.DATABASE_ERROR
                        && result.getBalance() != null) {
                    Cache.updateIntoCache(accountUid, pd, result.getBalance(), oldBalance);
                }
                return result;
            }

            Cache.updateIntoCache(accountUid, pd, result.getBalance(), oldBalance);
            recordLastIncome(isAdd, recordInfo, result.getTransactionId());
            if (XConomyLoad.getSyncData_Enable()) {
                SendMessTask(pd);
            }
            return result;
        }
    }

    public static BalanceTransferResult transferPlayerData(UUID senderUid, UUID targetUid,
                                                           BigDecimal senderAmount, BigDecimal targetAmount,
                                                           String command) {
        int senderLockIndex = getAccountLockIndex(senderUid);
        int targetLockIndex = getAccountLockIndex(targetUid);
        Object firstLock = ACCOUNT_LOCKS[Math.min(senderLockIndex, targetLockIndex)];
        Object secondLock = ACCOUNT_LOCKS[Math.max(senderLockIndex, targetLockIndex)];

        synchronized (firstLock) {
            if (firstLock == secondLock) {
                return transferPlayerDataLocked(senderUid, targetUid, senderAmount, targetAmount, command);
            }
            synchronized (secondLock) {
                return transferPlayerDataLocked(senderUid, targetUid, senderAmount, targetAmount, command);
            }
        }
    }

    private static BalanceTransferResult transferPlayerDataLocked(UUID senderUid, UUID targetUid,
                                                                  BigDecimal senderAmount,
                                                                  BigDecimal targetAmount,
                                                                  String command) {
        PlayerData sender = getPlayerData(senderUid);
        PlayerData target = getPlayerData(targetUid);
        if (sender == null || target == null) {
            return BalanceTransferResult.failure(BalanceMutationResult.Status.NOT_FOUND,
                    sender == null ? BigDecimal.ZERO : sender.getBalance(),
                    target == null ? BigDecimal.ZERO : target.getBalance());
        }

        BigDecimal oldSenderBalance = sender.getBalance();
        BigDecimal oldTargetBalance = target.getBalance();
        RecordInfo senderRecord = createRecordInfo("PLAYER_COMMAND", senderUid, false, command,
                "PAY_SEND:" + targetUid);
        RecordInfo targetRecord = createRecordInfo("PLAYER_COMMAND", targetUid, true, command,
                "PAY_RECEIVE:" + senderUid);

        CallAPI.CallPlayerAccountEvent(senderUid, sender.getName(), oldSenderBalance,
                senderAmount, false, senderRecord);
        CallAPI.CallPlayerAccountEvent(targetUid, target.getName(), oldTargetBalance,
                targetAmount, true, targetRecord);

        BalanceTransferResult result = executeTransfer(() -> DataLink.transfer(sender, target,
                senderAmount, targetAmount, senderRecord, targetRecord),
                BalanceTransferResult.failure(BalanceMutationResult.Status.DATABASE_ERROR,
                        oldSenderBalance, oldTargetBalance));

        if (!result.isSuccess()) {
            if (result.getStatus() != BalanceMutationResult.Status.DATABASE_ERROR) {
                Cache.updateIntoCache(senderUid, sender, result.getSenderBalance(), oldSenderBalance);
                Cache.updateIntoCache(targetUid, target, result.getTargetBalance(), oldTargetBalance);
            }
            return result;
        }

        Cache.updateIntoCache(senderUid, sender, result.getSenderBalance(), oldSenderBalance);
        Cache.updateIntoCache(targetUid, target, result.getTargetBalance(), oldTargetBalance);
        recordLastIncome(true, targetRecord, result.getTargetTransactionId());
        if (XConomyLoad.getSyncData_Enable()) {
            SendMessTask(sender);
            SendMessTask(target);
        }
        return result;
    }

    private static RecordInfo createRecordInfo(String type, UUID uid, Boolean isAdd,
                                               String command, Object comment) {
        RecordInfo recordInfo = new RecordInfo(type, command, comment);
        if (comment instanceof String) {
            String commentValue = (String) comment;
            try {
                if (commentValue.startsWith("PAY_SEND:")) {
                    recordInfo.withTracking(uid, UUID.fromString(commentValue.substring(9)), "PAY_SEND");
                } else if (commentValue.startsWith("PAY_RECEIVE:")) {
                    recordInfo.withTracking(UUID.fromString(commentValue.substring(12)), uid, "PAY_RECEIVE");
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (recordInfo.getTransactionType() == null) {
            if ("ADMIN_COMMAND".equals(type)) {
                recordInfo.withTracking(Boolean.FALSE.equals(isAdd) ? uid : null,
                        Boolean.FALSE.equals(isAdd) ? null : uid,
                        isAdd == null ? "ADMIN_SET" : isAdd ? "ADMIN_GIVE" : "ADMIN_TAKE");
            } else if ("PLUGIN_API".equals(type)) {
                recordInfo.withTracking(Boolean.FALSE.equals(isAdd) ? uid : null,
                        Boolean.FALSE.equals(isAdd) ? null : uid,
                        isAdd == null ? "PLUGIN_API_SET" : isAdd ? "PLUGIN_API_GIVE" : "PLUGIN_API_TAKE");
            } else if ("PLUGIN".equals(type)) {
                recordInfo.withTracking(Boolean.FALSE.equals(isAdd) ? uid : null,
                        Boolean.FALSE.equals(isAdd) ? null : uid,
                        isAdd == null ? "PLUGIN_SET" : isAdd ? "PLUGIN_GIVE" : "PLUGIN_TAKE");
            }
        }

        if (XConomyLoad.isTransactionTrackingEnabled() && isAdd != null) {
            Long parentId = null;
            if (isAdd && recordInfo.getFromUid() != null) {
                parentId = TraceManager.getLastIncome(recordInfo.getFromUid());
            } else if (!isAdd && recordInfo.getFromUid() != null) {
                parentId = TraceManager.getLastIncome(uid);
            }
            recordInfo.withTraceInfo(TraceManager.generateTraceId(), parentId);
        }
        return recordInfo;
    }

    private static void recordLastIncome(Boolean isAdd, RecordInfo recordInfo, Long transactionId) {
        if (XConomyLoad.isTransactionTrackingEnabled() && transactionId != null
                && Boolean.TRUE.equals(isAdd) && recordInfo.getToUid() != null) {
            TraceManager.recordLastIncome(recordInfo.getToUid(), transactionId);
        }
    }

    private static BalanceMutationResult executeMutation(
            java.util.function.Supplier<BalanceMutationResult> operation,
            BalanceMutationResult failure) {
        try {
            if (XConomyLoad.DConfig.canasync && AdapterManager.checkisMainThread()) {
                return CompletableFuture.supplyAsync(operation).join();
            }
            return operation.get();
        } catch (CompletionException exception) {
            exception.printStackTrace();
            return failure;
        }
    }

    private static BalanceTransferResult executeTransfer(
            java.util.function.Supplier<BalanceTransferResult> operation,
            BalanceTransferResult failure) {
        try {
            if (XConomyLoad.DConfig.canasync && AdapterManager.checkisMainThread()) {
                return CompletableFuture.supplyAsync(operation).join();
            }
            return operation.get();
        } catch (CompletionException exception) {
            exception.printStackTrace();
            return failure;
        }
    }

    private static Object getAccountLock(UUID uid) {
        return ACCOUNT_LOCKS[getAccountLockIndex(uid)];
    }

    private static int getAccountLockIndex(UUID uid) {
        return (uid.hashCode() & Integer.MAX_VALUE) % ACCOUNT_LOCKS.length;
    }

    @SuppressWarnings("ConstantConditions")
    public static void changeaccountdata(final String type, final String u, final BigDecimal amount, final Boolean isAdd, final String command) {
        BigDecimal newvalue = amount;
        BigDecimal balance = getAccountBalance(u);

        RecordInfo ri = new RecordInfo(type, command, null);

        CallAPI.CallNonPlayerAccountEvent(u, balance, amount, isAdd, type);
        if (isAdd != null) {
            if (isAdd) {
                newvalue = balance.add(amount);
            } else {
                newvalue = balance.subtract(amount);
            }
        }
        CacheNonPlayer.insertIntoCache(u, newvalue);

        if (XConomyLoad.DConfig.canasync && AdapterManager.checkisMainThread()) {
            final BigDecimal fnewvalue = newvalue;
            AdapterManager.runTaskAsynchronously(() -> DataLink.saveNonPlayer(u, amount, fnewvalue, isAdd, ri));
        } else {
            DataLink.saveNonPlayer(u, amount, newvalue, isAdd, ri);
        }
    }

    public static void changeallplayerdata(String targettype, String type, BigDecimal amount, Boolean isAdd, String command, StringBuilder comment) {
        Cache.clearCache();

        RecordInfo ri = new RecordInfo(type, command, comment);

        if (XConomyLoad.DConfig.canasync && AdapterManager.checkisMainThread()) {
            AdapterManager.runTaskAsynchronously(() -> DataLink.saveall(targettype, amount, isAdd, ri));
        } else {
            DataLink.saveall(targettype, amount, isAdd, ri);
        }

        boolean isallbool = targettype.equals("all");
        //if (targettype.equals("all")) {
        //} else

        if (XConomyLoad.getSyncData_Enable()) {
            SendMessTask(new SyncBalanceAll(isallbool, isAdd, amount));
        }
    }

    public static void baltop() {
        Cache.baltop.clear();
        Cache.baltop_papi.clear();
        sumbal();
        DataLink.getTopBal();
    }


    public static void sumbal() {
        Cache.sumbalance = DataFormat.formatString(DataLink.getBalSum());
    }


    public static void SendMessTask(SyncData pd) {
        if (XConomyLoad.Config.SYNCDATA_TYPE.equals(SyncChannalType.REDIS)) {
            RedisPublisher.publishmessage(pd.toByteArray(XConomy.syncversion).toByteArray());
        }else if (XConomyLoad.Config.SYNCDATA_TYPE.equals(SyncChannalType.BUNGEECORD)) {
            SendPluginMessage.SendMessTask("xconomy:acb", pd);
        }
    }

}
