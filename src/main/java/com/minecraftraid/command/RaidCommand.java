package com.minecraftraid.command;

import com.minecraftraid.MinecraftRaidPlugin;
import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.reinforcement.ReinforcementManager;
import com.minecraftraid.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RaidCommand implements CommandExecutor, TabCompleter {

    private final MinecraftRaidPlugin plugin;
    private final RaidConfig config;
    private final ReinforcementManager reinforcement;

    public RaidCommand(MinecraftRaidPlugin plugin, RaidConfig config, ReinforcementManager reinforcement) {
        this.plugin = plugin;
        this.config = config;
        this.reinforcement = reinforcement;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "claim" -> handleClaim(sender, args);
            case "unclaim" -> handleUnclaim(sender, args);
            case "trust" -> handleTrust(sender, args);
            case "untrust" -> handleUntrust(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "reinforce" -> handleReinforce(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /raid claim <radius> | unclaim [here|<id>] | trust <player> | untrust <player> | list | "
                + "reinforce <confirm|deny> <session> | "
                + "admin <safezone|warzone> <claim|unclaim> … | reload");
    }

    private boolean handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.claim")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /raid claim <radius>");
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Radius must be a whole number.");
            return true;
        }
        if (radius < config.claimMinRadius() || radius > config.claimMaxRadius()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("min", String.valueOf(config.claimMinRadius()));
            ph.put("max", String.valueOf(config.claimMaxRadius()));
            Messages.send(config, player, "claim-invalid-radius", ph);
            return true;
        }
        if (plugin.getClaimRegistry().countForOwner(player.getUniqueId()) >= config.claimMaxPerPlayer()) {
            Messages.send(config, player, "claim-too-many");
            return true;
        }
        String id = UUID.randomUUID().toString();
        LandClaim claim = new LandClaim(
                id,
                player.getWorld().getUID(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ(),
                radius,
                player.getUniqueId()
        );
        if (plugin.getClaimRegistry().overlapsAny(claim)) {
            Messages.send(config, player, "claim-overlap");
            return true;
        }
        if (plugin.getClaimRegionGuard().blocksClaim(claim, player.getWorld(), player.getLocation().getBlockY())) {
            Messages.send(config, player, "claim-worldguard-region");
            return true;
        }
        plugin.getClaimRegistry().add(claim);
        Map<String, String> ph = new HashMap<>();
        ph.put("radius", String.valueOf(radius));
        Messages.send(config, player, "claim-created", ph);
        return true;
    }

    private boolean handleUnclaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.claim")) {
            sender.sendMessage("No permission.");
            return true;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "here";
        if (mode.equals("here")) {
            LandClaim at = plugin.getClaimRegistry().anyClaimAt(player.getLocation());
            if (at == null || !plugin.getClaimRegistry().claimOwnedBy(player, player.getLocation())) {
                Messages.send(config, player, "no-claim-here");
                return true;
            }
            plugin.getClaimRegistry().remove(at.id());
            Messages.send(config, player, "claim-removed");
            return true;
        }
        LandClaim byId = plugin.getClaimRegistry().remove(mode);
        if (byId == null || !byId.kind().isPlayerOwned() || !byId.ownerUuid().equals(player.getUniqueId())) {
            Messages.send(config, player, "no-claim-here");
            return true;
        }
        Messages.send(config, player, "claim-removed");
        return true;
    }

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.trust")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /raid trust <player>");
            return true;
        }
        LandClaim at = plugin.getClaimRegistry().anyClaimAt(player.getLocation());
        if (at == null || at.isAdminZone()) {
            Messages.send(config, player, "trust-no-claim-here");
            return true;
        }
        if (!plugin.getClaimRegistry().claimOwnedBy(player, player.getLocation())) {
            Messages.send(config, player, "trust-not-owner");
            return true;
        }
        UUID targetId = resolvePlayerUuid(plugin, args[1]);
        if (targetId == null) {
            Messages.send(config, player, "trust-unknown-player");
            return true;
        }
        if (targetId.equals(player.getUniqueId())) {
            Messages.send(config, player, "trust-self");
            return true;
        }
        if (at.isTrusted(targetId)) {
            Messages.send(config, player, "trust-already");
            return true;
        }
        plugin.getClaimRegistry().replace(at.withTrustAdded(targetId));
        Map<String, String> ph = new HashMap<>();
        ph.put("player", args[1]);
        Messages.send(config, player, "trust-added", ph);
        return true;
    }

    private boolean handleUntrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.trust")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /raid untrust <player>");
            return true;
        }
        LandClaim at = plugin.getClaimRegistry().anyClaimAt(player.getLocation());
        if (at == null || at.isAdminZone()) {
            Messages.send(config, player, "trust-no-claim-here");
            return true;
        }
        if (!plugin.getClaimRegistry().claimOwnedBy(player, player.getLocation())) {
            Messages.send(config, player, "trust-not-owner");
            return true;
        }
        UUID targetId = resolvePlayerUuid(plugin, args[1]);
        if (targetId == null) {
            Messages.send(config, player, "trust-unknown-player");
            return true;
        }
        if (!at.isTrusted(targetId)) {
            Messages.send(config, player, "trust-not-trusted");
            return true;
        }
        plugin.getClaimRegistry().replace(at.withTrustRemoved(targetId));
        Map<String, String> ph = new HashMap<>();
        ph.put("player", args[1]);
        Messages.send(config, player, "trust-removed", ph);
        return true;
    }

    private static UUID resolvePlayerUuid(MinecraftRaidPlugin plugin, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
        }
        Player online = plugin.getServer().getPlayerExact(trimmed);
        if (online != null) {
            return online.getUniqueId();
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer off = plugin.getServer().getOfflinePlayer(trimmed);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return off.getUniqueId();
        }
        return null;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.claim")) {
            sender.sendMessage("No permission.");
            return true;
        }
        List<LandClaim> owned = plugin.getClaimRegistry().ownedBy(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage("You have no claims.");
            return true;
        }
        player.sendMessage("Your claims (" + owned.size() + "):");
        for (LandClaim c : owned) {
            player.sendMessage("  id=" + c.id() + " r=" + c.radiusBlocks() + " cx=" + c.centerBlockX() + " cz=" + c.centerBlockZ());
        }
        return true;
    }

    private boolean handleReinforce(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.reinforce")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /raid reinforce confirm <session> | /raid reinforce deny <session>");
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        UUID sessionId;
        try {
            sessionId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid session id.");
            return true;
        }
        if (mode.equals("confirm")) {
            return reinforcement.handleConfirm(player, sessionId);
        }
        if (mode.equals("deny")) {
            return reinforcement.handleDeny(player, sessionId);
        }
        sender.sendMessage("Usage: /raid reinforce confirm <session> | /raid reinforce deny <session>");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("minecraftraid.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        plugin.reloadRaidConfig();
        Messages.send(config, sender, "reload-done");
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minecraftraid.admin.zones") && !player.hasPermission("minecraftraid.admin")) {
            Messages.send(config, player, "admin-zones-no-permission");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage("Usage: /raid admin <safezone|warzone> claim <radius> | /raid admin <safezone|warzone> unclaim <here|id>");
            return true;
        }
        ClaimKind zoneKind = zoneKindArg(args[1]);
        if (zoneKind == null) {
            Messages.send(config, player, "admin-zones-unknown-type");
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("claim".equals(action)) {
            int radius;
            try {
                radius = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("Radius must be a whole number.");
                return true;
            }
            if (radius < config.claimMinRadius() || radius > config.claimMaxRadius()) {
                Map<String, String> ph = new HashMap<>();
                ph.put("min", String.valueOf(config.claimMinRadius()));
                ph.put("max", String.valueOf(config.claimMaxRadius()));
                Messages.send(config, player, "claim-invalid-radius", ph);
                return true;
            }
            String id = UUID.randomUUID().toString();
            LandClaim candidate = LandClaim.adminZone(
                    id,
                    player.getWorld().getUID(),
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ(),
                    radius,
                    zoneKind);
            if (plugin.getClaimRegistry().overlapsAny(candidate)) {
                Messages.send(config, player, "admin-zone-overlap");
                return true;
            }
            plugin.getClaimRegistry().add(candidate);
            Map<String, String> ph = new HashMap<>();
            ph.put("type", zoneKind == ClaimKind.SAFE_ZONE ? "Safe zone" : "War zone");
            ph.put("radius", String.valueOf(radius));
            Messages.send(config, player, "admin-zone-created", ph);
            return true;
        }
        if ("unclaim".equals(action)) {
            LandClaim removed;
            if ("here".equalsIgnoreCase(args[3])) {
                LandClaim at = plugin.getClaimRegistry().anyClaimAt(player.getLocation());
                if (at == null || at.kind() != zoneKind) {
                    Messages.send(config, player, "admin-zone-not-here");
                    return true;
                }
                removed = plugin.getClaimRegistry().remove(at.id());
            } else {
                LandClaim byId = plugin.getClaimRegistry().remove(args[3]);
                if (byId == null || byId.kind() != zoneKind) {
                    Messages.send(config, player, "admin-zone-not-found");
                    return true;
                }
                removed = byId;
            }
            if (removed != null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("id", removed.id());
                Messages.send(config, player, "admin-zone-removed", ph);
            }
            return true;
        }
        player.sendMessage("Usage: /raid admin <safezone|warzone> claim <radius> | unclaim <here|id>");
        return true;
    }

    /** SAFE_ZONE / WAR_ZONE from sub-arg, or null. */
    private static ClaimKind zoneKindArg(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if ("safezone".equals(s) || "safe".equals(s)) {
            return ClaimKind.SAFE_ZONE;
        }
        if ("warzone".equals(s) || "war".equals(s)) {
            return ClaimKind.WAR_ZONE;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("claim", "unclaim", "trust", "untrust", "list", "reinforce", "admin", "reload")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            if ("safezone".startsWith(p)) {
                out.add("safezone");
            }
            if ("warzone".startsWith(p)) {
                out.add("warzone");
            }
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0])) {
            String p = args[2].toLowerCase(Locale.ROOT);
            if ("claim".startsWith(p)) {
                out.add("claim");
            }
            if ("unclaim".startsWith(p)) {
                out.add("unclaim");
            }
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(args[0])) {
            if ("unclaim".equalsIgnoreCase(args[2])) {
                String p = args[3].toLowerCase(Locale.ROOT);
                if ("here".startsWith(p)) {
                    out.add("here");
                }
            }
        }
        if (args.length == 2 && "unclaim".equalsIgnoreCase(args[0])) {
            out.add("here");
        }
        if (args.length == 2 && "reinforce".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            if ("confirm".startsWith(p)) {
                out.add("confirm");
            }
            if ("deny".startsWith(p)) {
                out.add("deny");
            }
        }
        if (args.length == 2 && ("trust".equalsIgnoreCase(args[0]) || "untrust".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player online : sender.getServer().getOnlinePlayers()) {
                String name = online.getName();
                if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            Collections.sort(out);
        }
        return out;
    }
}
