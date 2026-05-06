package com.minecraftraid.listener;

import com.minecraftraid.MinecraftRaidPlugin;
import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.Messages;
import com.minecraftraid.util.RaidChestLinkage;
import com.minecraftraid.util.RaidMaterials;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Raid blocks: any block placed inside a PLAYER claim where the placer is a member becomes a raid block.
 * Stored {@link RaidBlock#ownerUuid()} is always the claim owner (trusted helpers do not retain separate ownership).
 * <p>Future: log placements via CoreProtect (or similar) API so players can pay to rollback a destroyed base.</p>
 */
public final class RaidBlockListener implements Listener {

    private final MinecraftRaidPlugin plugin;
    private final RaidConfig config;
    private final RaidBlockRegistry blocks;

    public RaidBlockListener(MinecraftRaidPlugin plugin, RaidConfig config, RaidBlockRegistry blocks) {
        this.plugin = plugin;
        this.config = config;
        this.blocks = blocks;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        if (!plugin.getClaimRegistry().isClaimMember(player, placed.getLocation())) {
            return;
        }
        Material type = placed.getType();
        if (config.isBlockedMaterial(type)) {
            event.setCancelled(true);
            Messages.send(config, player, "blocked-material");
            return;
        }
        LandClaim covering = plugin.getClaimRegistry().anyClaimAt(placed.getLocation());
        UUID raidOwnerUuid = (covering != null && covering.kind().isPlayerOwned())
                ? covering.ownerUuid()
                : player.getUniqueId();
        int maxHp = config.maxHpFor(type);
        RaidBlock rb = new RaidBlock(
                placed.getWorld().getUID(),
                placed.getX(),
                placed.getY(),
                placed.getZ(),
                raidOwnerUuid,
                maxHp,
                maxHp,
                type
        );
        blocks.put(rb);
        RaidChestLinkage.scheduleTryMerge(plugin, blocks, placed);
        if (config.notifyOnRaidPlace()) {
            Messages.send(config, player, "placed-raid-block");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        RaidBlock rb = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (rb == null) {
            return;
        }
        if (rb.breached()) {
            blocks.remove(block.getWorld(), block.getX(), block.getY(), block.getZ());
            return;
        }
        if (plugin.getClaimRegistry().isClaimMember(player, block.getLocation())) {
            blocks.remove(block.getWorld(), block.getX(), block.getY(), block.getZ());
            return;
        }
        if (config.requireOwnerOnlineForRaidDamage()
                && plugin.getServer().getPlayer(rb.ownerUuid()) == null) {
            event.setCancelled(true);
            Messages.send(config, player, "owner-offline-raid-damage");
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            event.setCancelled(true);
            blocks.remove(block.getWorld(), block.getX(), block.getY(), block.getZ());
            block.setType(Material.AIR, false);
            return;
        }
        event.setCancelled(true);
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material toolType = tool.getType();
        int dmg = config.miningDamageForTool(toolType);
        int newHp = rb.currentHp() - dmg;
        if (newHp <= 0) {
            if (RaidMaterials.isRaidContainer(rb.material())) {
                blocks.replace(rb.withHp(0).withBreached(true));
                player.sendBlockDamage(block.getLocation(), 0f);
                return;
            }
            blocks.remove(block.getWorld(), block.getX(), block.getY(), block.getZ());
            block.breakNaturally(tool, true, true);
            player.sendBlockDamage(block.getLocation(), 0f);
            return;
        }
        RaidBlock updated = rb.withHp(newHp);
        blocks.replace(updated);
        float progress = 1f - (updated.currentHp() / (float) updated.maxHp());
        player.sendBlockDamage(block.getLocation(), Math.min(0.99f, Math.max(0.01f, progress)));
        Map<String, String> ph = new HashMap<>();
        ph.put("current", String.valueOf(updated.currentHp()));
        ph.put("max", String.valueOf(updated.maxHp()));
        Messages.actionBar(config, player, "damage-hp", ph);
    }
}
