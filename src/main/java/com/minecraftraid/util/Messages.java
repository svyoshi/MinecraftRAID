package com.minecraftraid.util;

import com.minecraftraid.config.RaidConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

/** Chat + action-bar helpers using MiniMessage. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {
    }

    public static void send(RaidConfig config, CommandSender sender, String key) {
        send(config, sender, key, null);
    }

    public static void send(RaidConfig config, CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = config.message(key, placeholders);
        Component c = MM.deserialize(raw);
        sender.sendMessage(c);
    }

    public static void actionBar(RaidConfig config, Player player, String key, Map<String, String> placeholders) {
        String raw = config.message(key, placeholders);
        Component c = MM.deserialize(raw);
        player.sendActionBar(c);
    }

    /** Sends MiniMessage body without adding {@code prefix} from config (e.g. clickable lines). */
    public static void sendMiniBody(Player player, RaidConfig config, String key, Map<String, String> placeholders) {
        String raw = config.miniMessageTemplate(key, placeholders);
        player.sendMessage(MM.deserialize(raw));
    }

    /** Empty map convenience for callers with no placeholders. */
    public static Map<String, String> noPh() {
        return Map.of();
    }

    /** MiniMessage title + subtitle without {@code messages.prefix}. Skips if both strings are blank after trim. */
    public static void showTitleMini(
            Player player,
            String miniTitle,
            String miniSubtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks) {
        String tRaw = miniTitle == null ? "" : miniTitle.trim();
        String sRaw = miniSubtitle == null ? "" : miniSubtitle.trim();
        if (tRaw.isEmpty() && sRaw.isEmpty()) {
            return;
        }
        Component title = tRaw.isEmpty() ? Component.empty() : MM.deserialize(tRaw);
        Component sub = sRaw.isEmpty() ? Component.empty() : MM.deserialize(sRaw);
        Duration unit = Duration.ofMillis(50);
        Title.Times times = Title.Times.times(
                unit.multipliedBy(Math.max(0, fadeInTicks)),
                unit.multipliedBy(Math.max(0, stayTicks)),
                unit.multipliedBy(Math.max(0, fadeOutTicks)));
        player.showTitle(Title.title(title, sub, times));
    }
}
