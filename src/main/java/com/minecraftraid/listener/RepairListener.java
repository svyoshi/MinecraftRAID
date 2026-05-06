package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class RepairListener implements Listener {

    private final RaidConfig config;
    private final RaidBlockRegistry blocks;

    public RepairListener(RaidConfig config, RaidBlockRegistry blocks) {
        this.config = config;
        this.blocks = blocks;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // One physical click fires PlayerInteractEvent for main and off hand; only handle main hand.
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        RaidBlock rb = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (rb == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(rb.ownerUuid())) {
            return;
        }
        if (rb.currentHp() >= rb.maxHp()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!config.repairToolMaterials().contains(hand.getType())) {
            Messages.send(config, player, "wrong-tool");
            event.setCancelled(true);
            return;
        }
        Material blockMat = rb.material();
        if (!blockMat.isItem()) {
            Messages.send(config, player, "repair-not-itemable");
            event.setCancelled(true);
            return;
        }
        var notRemoved = player.getInventory().removeItem(new ItemStack(blockMat, 1));
        if (!notRemoved.isEmpty()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("material", formatMaterialName(blockMat));
            Messages.send(config, player, "not-enough-material", ph);
            event.setCancelled(true);
            return;
        }
        RaidBlock updated = rb.withHp(rb.maxHp()).withBreached(false);
        blocks.replace(updated);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.2f);
        Messages.send(config, player, "repaired");
        event.setCancelled(true);
    }

    private static String formatMaterialName(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
