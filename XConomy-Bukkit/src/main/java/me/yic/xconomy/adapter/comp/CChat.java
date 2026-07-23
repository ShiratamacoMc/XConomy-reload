package me.yic.xconomy.adapter.comp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class CChat {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final Pattern LEGACY_COLOR_CODE =
            Pattern.compile("(?i)(?:&|§)[0-9a-fk-or]|&#[0-9a-f]{6}");
    private static final Set<String> WARNED_LEGACY_MESSAGES = ConcurrentHashMap.newKeySet();

    public static String translateAlternateColorCodes(Character cha, String str){
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (LEGACY_COLOR_CODE.matcher(str).find() && WARNED_LEGACY_MESSAGES.add(str)) {
            Bukkit.getLogger().warning("[XConomy] Legacy color codes are not supported. "
                    + "Use MiniMessage formatting instead: " + str);
        }
        try {
            MINI_MESSAGE.deserialize(str);
            return str;
        } catch (RuntimeException exception) {
            Bukkit.getLogger().warning("[XConomy] Invalid MiniMessage text: " + str);
            return MINI_MESSAGE.serialize(Component.text(str));
        }
    }

    public static String toLegacy(String message) {
        return LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(message));
    }

}
