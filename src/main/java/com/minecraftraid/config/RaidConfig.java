package com.minecraftraid.config;

import com.minecraftraid.model.ClaimKind;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class RaidConfig {

    private int defaultMaxHp;
    private final Map<Material, Integer> materialHp = new HashMap<>();
    private final Set<Material> blockedMaterials = EnumSet.noneOf(Material.class);
    private int defaultMiningDamage;
    private final Map<Material, Integer> toolMiningDamage = new EnumMap<>(Material.class);
    private double explosionDamageScale;
    private boolean cancelPistonMove;
    private boolean requireOwnerOnlineForRaidDamage;

    private final Set<Material> repairToolMaterials = EnumSet.noneOf(Material.class);

    private int saveIntervalTicks;

    private int claimMinRadius;
    private int claimMaxRadius;
    private int claimMaxPerPlayer;
    private boolean protectClaimBlocks;
    private boolean protectClaimEntities;
    private boolean allowSameOwnerOverlap;
    private boolean blockOtherOverlap;

    private boolean borderVisualEnabled;
    private int borderVisualIntervalTicks;
    private int borderBandBlocks;
    private int borderViewRadius;
    private int borderSegments;
    private int borderMaxUpdatesPerTick;

    private boolean zoneTitlesEnabled;
    private int zoneTitleFadeInTicks;
    private int zoneTitleStayTicks;
    private int zoneTitleFadeOutTicks;

    /** MiniMessage templates; no chat prefix applied. SAFE/WAR only. */
    private String zoneTsEnterTitle = "";
    private String zoneTsEnterSubtitle = "";
    private String zoneTsLeaveTitle = "";
    private String zoneTsLeaveSubtitle = "";

    private String zoneTwEnterTitle = "";
    private String zoneTwEnterSubtitle = "";
    private String zoneTwLeaveTitle = "";
    private String zoneTwLeaveSubtitle = "";

    private boolean notifyOnRaidPlace;
    private int lookDurabilityIntervalTicks;
    private double lookDurabilityMaxDistance;

    private String msgPrefix;
    private final Map<String, String> rawMessages = new HashMap<>();

    private int reinforcementHpPerTier;
    private Material reinforcementTier1Material;
    private Material reinforcementTier2Material;
    private Material reinforcementTier3Material;
    private int reinforcementXpBasePerBlock;
    private int reinforcementXpPerBlockPerTargetTier;
    private int reinforcementMaxBlocksPerSelection;
    private int reinforcementSessionTimeoutTicks;

    public void reload(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        defaultMaxHp = c.getInt("base-mode.default-max-hp", 100);
        materialHp.clear();
        ConfigurationSection matHp = c.getConfigurationSection("base-mode.material-hp");
        if (matHp != null) {
            for (String key : matHp.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) {
                    materialHp.put(m, matHp.getInt(key));
                }
            }
        }

        blockedMaterials.clear();
        for (String s : c.getStringList("base-mode.blocked-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                blockedMaterials.add(m);
            }
        }

        defaultMiningDamage = c.getInt("base-mode.default-mining-damage", 10);
        toolMiningDamage.clear();
        ConfigurationSection toolDmg = c.getConfigurationSection("base-mode.tool-mining-damage");
        if (toolDmg != null) {
            for (String key : toolDmg.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) {
                    toolMiningDamage.put(m, toolDmg.getInt(key));
                }
            }
        }

        explosionDamageScale = c.getDouble("base-mode.explosion-damage-scale", 80.0);
        cancelPistonMove = c.getBoolean("base-mode.cancel-piston-move", true);
        requireOwnerOnlineForRaidDamage = c.getBoolean("base-mode.require-owner-online-for-damage", true);

        repairToolMaterials.clear();
        for (String s : c.getStringList("repair.tool-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                repairToolMaterials.add(m);
            }
        }

        saveIntervalTicks = c.getInt("persistence.save-interval-ticks", 600);

        claimMinRadius = c.getInt("claims.min-radius", 5);
        claimMaxRadius = c.getInt("claims.max-radius", 128);
        claimMaxPerPlayer = c.getInt("claims.max-per-player", 5);
        protectClaimBlocks = c.getBoolean("claims.protect-blocks", true);
        protectClaimEntities = c.getBoolean("claims.protect-entities", true);
        allowSameOwnerOverlap = c.getBoolean("claims.allow-same-owner-overlap", true);
        blockOtherOverlap = c.getBoolean("claims.block-other-overlap", true);

        borderVisualEnabled = c.getBoolean("claims.border-visual-enabled", true);
        borderVisualIntervalTicks = c.getInt("claims.border-visual-interval-ticks", 5);
        borderBandBlocks = c.getInt("claims.border-band-blocks", 4);
        borderViewRadius = c.getInt("claims.border-view-radius", 40);
        borderSegments = Math.max(8, c.getInt("claims.border-segments", 96));
        borderMaxUpdatesPerTick = Math.max(8, c.getInt("claims.border-max-updates-per-tick", 72));

        zoneTitlesEnabled = c.getBoolean("claims.zone-titles-enabled", true);
        zoneTitleFadeInTicks = Math.max(0, c.getInt("claims.zone-title-fade-in-ticks", 10));
        zoneTitleStayTicks = Math.max(0, c.getInt("claims.zone-title-stay-ticks", 70));
        zoneTitleFadeOutTicks = Math.max(0, c.getInt("claims.zone-title-fade-out-ticks", 20));
        zoneTsEnterTitle = c.getString("claims.zone-titles.safe-zone.enter-title", "");
        zoneTsEnterSubtitle = c.getString("claims.zone-titles.safe-zone.enter-subtitle", "");
        zoneTsLeaveTitle = c.getString("claims.zone-titles.safe-zone.leave-title", "");
        zoneTsLeaveSubtitle = c.getString("claims.zone-titles.safe-zone.leave-subtitle", "");
        zoneTwEnterTitle = c.getString("claims.zone-titles.war-zone.enter-title", "");
        zoneTwEnterSubtitle = c.getString("claims.zone-titles.war-zone.enter-subtitle", "");
        zoneTwLeaveTitle = c.getString("claims.zone-titles.war-zone.leave-title", "");
        zoneTwLeaveSubtitle = c.getString("claims.zone-titles.war-zone.leave-subtitle", "");

        notifyOnRaidPlace = c.getBoolean("base-mode.notify-on-place", false);
        lookDurabilityIntervalTicks = c.getInt("base-mode.look-durability-interval-ticks", 5);
        lookDurabilityMaxDistance = c.getDouble("base-mode.look-durability-max-distance", 2.0);

        reinforcementHpPerTier = Math.max(1, c.getInt("reinforcement.hp-per-tier", 200));
        reinforcementTier1Material = parseMat(c, "reinforcement.tier1-material", Material.STONE);
        reinforcementTier2Material = parseMat(c, "reinforcement.tier2-material", Material.IRON_BLOCK);
        reinforcementTier3Material = parseMat(c, "reinforcement.tier3-material", Material.OBSIDIAN);
        reinforcementXpBasePerBlock = Math.max(0, c.getInt("reinforcement.xp-base-per-block", 5));
        reinforcementXpPerBlockPerTargetTier = Math.max(0, c.getInt("reinforcement.xp-per-block-per-target-tier", 2));
        reinforcementMaxBlocksPerSelection = Math.max(1, c.getInt("reinforcement.max-blocks-per-selection", 512));
        reinforcementSessionTimeoutTicks = Math.max(200, c.getInt("reinforcement.session-timeout-ticks", 2400));

        ConfigurationSection msg = c.getConfigurationSection("messages");
        rawMessages.clear();
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                rawMessages.put(key, msg.getString(key, ""));
            }
        }
        msgPrefix = rawMessages.getOrDefault("prefix", "");
    }

    private static Material parseMat(FileConfiguration c, String path, Material def) {
        Material m = Material.matchMaterial(c.getString(path, def.name()));
        return m != null ? m : def;
    }

    /** MiniMessage template without the global prefix (for clickable lines etc.). */
    public String miniMessageTemplate(String key, Map<String, String> placeholders) {
        String template = rawMessages.getOrDefault(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                template = template.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return template;
    }

    public int reinforcementHpPerTier() {
        return reinforcementHpPerTier;
    }

    public Material reinforcementTier1Material() {
        return reinforcementTier1Material;
    }

    public Material reinforcementTier2Material() {
        return reinforcementTier2Material;
    }

    public Material reinforcementTier3Material() {
        return reinforcementTier3Material;
    }

    public int reinforcementXpBasePerBlock() {
        return reinforcementXpBasePerBlock;
    }

    public int reinforcementXpPerBlockPerTargetTier() {
        return reinforcementXpPerBlockPerTargetTier;
    }

    public int reinforcementMaxBlocksPerSelection() {
        return reinforcementMaxBlocksPerSelection;
    }

    public int reinforcementSessionTimeoutTicks() {
        return reinforcementSessionTimeoutTicks;
    }

    public int maxHpFor(Material placed) {
        return materialHp.getOrDefault(placed, defaultMaxHp);
    }

    public boolean isBlockedMaterial(Material m) {
        return blockedMaterials.contains(m) || !m.isBlock() || m.isAir();
    }

    public int miningDamageForTool(Material tool) {
        return toolMiningDamage.getOrDefault(tool, defaultMiningDamage);
    }

    public double explosionDamageScale() {
        return explosionDamageScale;
    }

    public boolean cancelPistonMove() {
        return cancelPistonMove;
    }

    public boolean requireOwnerOnlineForRaidDamage() {
        return requireOwnerOnlineForRaidDamage;
    }

    public Set<Material> repairToolMaterials() {
        return Collections.unmodifiableSet(repairToolMaterials);
    }

    public int saveIntervalTicks() {
        return saveIntervalTicks;
    }

    public int claimMinRadius() {
        return claimMinRadius;
    }

    public int claimMaxRadius() {
        return claimMaxRadius;
    }

    public int claimMaxPerPlayer() {
        return claimMaxPerPlayer;
    }

    public boolean protectClaimBlocks() {
        return protectClaimBlocks;
    }

    public boolean protectClaimEntities() {
        return protectClaimEntities;
    }

    public boolean notifyOnRaidPlace() {
        return notifyOnRaidPlace;
    }

    public int lookDurabilityIntervalTicks() {
        return lookDurabilityIntervalTicks;
    }

    public double lookDurabilityMaxDistance() {
        return lookDurabilityMaxDistance;
    }

    public boolean allowSameOwnerOverlap() {
        return allowSameOwnerOverlap;
    }

    public boolean blockOtherOverlap() {
        return blockOtherOverlap;
    }

    public boolean borderVisualEnabled() {
        return borderVisualEnabled;
    }

    public int borderVisualIntervalTicks() {
        return borderVisualIntervalTicks;
    }

    public int borderBandBlocks() {
        return borderBandBlocks;
    }

    public int borderViewRadius() {
        return borderViewRadius;
    }

    public int borderSegments() {
        return borderSegments;
    }

    public int borderMaxUpdatesPerTick() {
        return borderMaxUpdatesPerTick;
    }

    public boolean zoneTitlesEnabled() {
        return zoneTitlesEnabled;
    }

    public int zoneTitleFadeInTicks() {
        return zoneTitleFadeInTicks;
    }

    public int zoneTitleStayTicks() {
        return zoneTitleStayTicks;
    }

    public int zoneTitleFadeOutTicks() {
        return zoneTitleFadeOutTicks;
    }

    /** ClaimKind must be {@link ClaimKind#SAFE_ZONE} or {@link ClaimKind#WAR_ZONE}. */
    public String zoneEnterTitle(ClaimKind kind) {
        return kind == ClaimKind.SAFE_ZONE ? nullToEmpty(zoneTsEnterTitle) : nullToEmpty(zoneTwEnterTitle);
    }

    public String zoneEnterSubtitle(ClaimKind kind) {
        return kind == ClaimKind.SAFE_ZONE ? nullToEmpty(zoneTsEnterSubtitle) : nullToEmpty(zoneTwEnterSubtitle);
    }

    public String zoneLeaveTitle(ClaimKind kind) {
        return kind == ClaimKind.SAFE_ZONE ? nullToEmpty(zoneTsLeaveTitle) : nullToEmpty(zoneTwLeaveTitle);
    }

    public String zoneLeaveSubtitle(ClaimKind kind) {
        return kind == ClaimKind.SAFE_ZONE ? nullToEmpty(zoneTsLeaveSubtitle) : nullToEmpty(zoneTwLeaveSubtitle);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public String message(String key, Map<String, String> placeholders) {
        String template = rawMessages.getOrDefault(key, key);
        String out = msgPrefix + template;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return out;
    }

    public String message(String key) {
        return message(key, null);
    }

}
