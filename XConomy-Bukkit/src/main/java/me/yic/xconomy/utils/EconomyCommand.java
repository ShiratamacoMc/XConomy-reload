/*
 *  This file (EconomyCommand.java) is a part of project XConomy
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
package me.yic.xconomy.utils;

import me.yic.xconomy.command.core.CommandCore;
import me.yic.xconomy.command.CommandDefinition;
import me.yic.xconomy.adapter.comp.CSender;
import me.yic.xconomy.listeners.TabList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EconomyCommand extends Command {
    private final String name;
    private final TabCompleter tabCompleter = new TabList();

    public EconomyCommand(CommandDefinition definition) {
        super(definition.getName());
        this.name = definition.getName();
        this.description = definition.getDescription();
        this.usageMessage = definition.getUsage();
        this.setAliases(definition.getAliases());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) {
        return CommandCore.onCommand(new CSender(sender), name, args);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return tabCompleter.onTabComplete(sender, this, name, args);
    }

}
