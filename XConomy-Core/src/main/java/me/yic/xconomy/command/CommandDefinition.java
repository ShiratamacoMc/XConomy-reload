package me.yic.xconomy.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandDefinition {
    private final String name;
    private final List<String> aliases;
    private final String description;
    private final String usage;
    private final boolean optional;

    CommandDefinition(String name, List<String> aliases, String description, String usage, boolean optional) {
        this.name = name;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
        this.description = description;
        this.usage = usage;
        this.optional = optional;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public boolean isOptional() {
        return optional;
    }

    boolean matches(String commandName) {
        if (name.equalsIgnoreCase(commandName)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(commandName)) {
                return true;
            }
        }
        return false;
    }
}
