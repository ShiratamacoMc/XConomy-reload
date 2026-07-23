package me.yic.xconomy.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CommandRegistry {
    private static final List<CommandDefinition> COMMANDS = Collections.unmodifiableList(Arrays.asList(
            command("xconomy", "XConomy administration", "/xconomy <help|reload|deldata|track>",
                    false, "xc"),
            command("money", "View and manage balances", "/money [look <player>]",
                    false),
            command("balance", "View and manage balances", "/balance [look <player>]",
                    false, "bal"),
            command("balancetop", "View the balance leaderboard", "/balancetop [hide|display <player>]",
                    false, "baltop"),
            command("pay", "Send money to another player", "/pay <player> <amount>",
                    false),
            command("paytoggle", "Toggle payment requests", "/paytoggle [player]",
                    false),
            command("paypermission", "Manage payment permissions", "/paypermission <set|remove> <player>",
                    false, "payperm"),
            command("economy", "Essentials-compatible balance command", "/economy [look <player>]",
                    true, "eco", "eeconomy"),
            command("ebalancetop", "Essentials-compatible balance leaderboard", "/ebalancetop",
                    true, "ebaltop")
    ));

    private CommandRegistry() {
    }

    public static List<CommandDefinition> getCommands(boolean includeOptionalCommands) {
        List<CommandDefinition> commands = new ArrayList<>();
        for (CommandDefinition command : COMMANDS) {
            if (includeOptionalCommands || !command.isOptional()) {
                commands.add(command);
            }
        }
        return Collections.unmodifiableList(commands);
    }

    public static String resolve(String commandName) {
        for (CommandDefinition command : COMMANDS) {
            if (command.matches(commandName)) {
                return command.getName();
            }
        }
        return null;
    }

    private static CommandDefinition command(String name, String description, String usage,
                                             boolean optional, String... aliases) {
        return new CommandDefinition(name, Arrays.asList(aliases), description, usage, optional);
    }
}
