package com.minedkibbles21.kibblecommands.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtil {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    public static Component colorize(String text) {
        return SERIALIZER.deserialize(text);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(MessageUtil.colorize(message));
    }

    public static void send(CommandSender sender, String template, String ... pairs) {
        String result = template;
        int i = 0;
        while (i + 1 < pairs.length) {
            result = result.replace(pairs[i], pairs[i + 1]);
            i += 2;
        }
        MessageUtil.send(sender, result);
    }

    public static String strip(String text) {
        return text.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}

