/*
 *  This file (TransactionRecord.java) is a part of project XConomy
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
import java.util.Date;
import java.util.UUID;

public class TransactionRecord {
    private final int id;
    private final String type;
    private final UUID uid;
    private final String player;
    private final BigDecimal balance;
    private final BigDecimal amount;
    private final String operation;
    private final String command;
    private final String comment;
    private final Date datetime;
    private final UUID fromUid;
    private final UUID toUid;
    private final String transactionType;
    private final String serverId;
    private final String traceId;
    private final Long parentTransactionId;

    public TransactionRecord(int id, String type, UUID uid, String player, BigDecimal balance,
                           BigDecimal amount, String operation, String command, String comment,
                           Date datetime, UUID fromUid, UUID toUid, String transactionType, String serverId,
                           String traceId, Long parentTransactionId) {
        this.id = id;
        this.type = type;
        this.uid = uid;
        this.player = player;
        this.balance = balance;
        this.amount = amount;
        this.operation = operation;
        this.command = command;
        this.comment = comment;
        this.datetime = datetime;
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.transactionType = transactionType;
        this.serverId = serverId;
        this.traceId = traceId;
        this.parentTransactionId = parentTransactionId;
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public UUID getUid() {
        return uid;
    }

    public String getPlayer() {
        return player;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getOperation() {
        return operation;
    }

    public String getCommand() {
        return command;
    }

    public String getComment() {
        return comment;
    }

    public Date getDatetime() {
        return datetime;
    }

    public UUID getFromUid() {
        return fromUid;
    }

    public UUID getToUid() {
        return toUid;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getServerId() {
        return serverId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Long getParentTransactionId() {
        return parentTransactionId;
    }
}

