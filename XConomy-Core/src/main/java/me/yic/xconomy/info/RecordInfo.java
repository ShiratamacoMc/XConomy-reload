/*
 *  This file (RecordInfo.java) is a part of project XConomy
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
package me.yic.xconomy.info;

import me.yic.xconomy.XConomy;

import java.util.UUID;

public class RecordInfo {
    private final String type;

    private final String command;

    private final String comment;

    private UUID fromUid;
    private UUID toUid;
    private String transactionType;
    private String traceId;
    private Long parentTransactionId;

    public RecordInfo(String type, String command, Object comment) {
        if (command == null) {
            this.type = type;
            this.command = Thread.currentThread().getStackTrace()[getindex()].getClassName();
            this.comment = "N/A";
        }else{
            if (comment == null) {
                this.type = type;
                this.command = command;
                this.comment = "N/A";
            }else{
                this.type = type;
                this.command = command;
                if (comment instanceof StringBuilder){
                    this.comment = comment.toString();
                }else {
                    this.comment = (String) comment;
                }
            }
        }
    }

    private int getindex() {
        if (XConomy.version.equals("Bukkit")) {
            return 4;
        }
        return 5;
    }

    public String getType() {
        return type;
    }

    public String getCommand() {
        return command;
    }

    public String getComment() {
        return comment;
    }

    public UUID getFromUid() {
        return fromUid;
    }

    public void setFromUid(UUID fromUid) {
        this.fromUid = fromUid;
    }

    public UUID getToUid() {
        return toUid;
    }

    public void setToUid(UUID toUid) {
        this.toUid = toUid;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getParentTransactionId() {
        return parentTransactionId;
    }

    public void setParentTransactionId(Long parentTransactionId) {
        this.parentTransactionId = parentTransactionId;
    }

    public RecordInfo withTracking(UUID fromUid, UUID toUid, String transactionType) {
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.transactionType = transactionType;
        return this;
    }

    public RecordInfo withTraceInfo(String traceId, Long parentTransactionId) {
        this.traceId = traceId;
        this.parentTransactionId = parentTransactionId;
        return this;
    }
}