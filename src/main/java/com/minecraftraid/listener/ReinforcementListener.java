package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.reinforcement.ReinforcementManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Shift–left-click with repair tool: cuboid reinforcement selection. */
public final class ReinforcementListener implements Listener {

    private final RaidConfig config;
    private final ReinforcementManager reinforcement;

    public ReinforcementListener(RaidConfig config, ReinforcementManager reinforcement) {
        this.config = config;
        this.reinforcement = reinforcement;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasBlock() || !event.getPlayer().isSneaking()) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!config.repairToolMaterials().contains(hand.getType())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        reinforcement.handleSelectCorner(player, block);
    }
}
