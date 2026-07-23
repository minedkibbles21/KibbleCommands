package com.minedkibbles21.kibblecommands.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    public static Component parse(String text) {
        return LEGACY.deserialize(text);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }

    public static void send(CommandSender sender, String template, String... replacements) {
        String formatted = template;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            formatted = formatted.replace(replacements[i], replacements[i + 1]);
        }
        send(sender, formatted);
    }
}
