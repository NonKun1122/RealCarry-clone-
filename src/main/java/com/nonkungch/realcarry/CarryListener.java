package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.UUID;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    private final CarryingManager carryingManager;

    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    public CarryListener(RealCarry plugin, CarryingManager carryingManager) {
        this.plugin = plugin;
        this.carryingManager = carryingManager;
    }

    private boolean onCooldown(Player p) {
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);
        return now - last < 200;
    }

    private void setCooldown(Player p) {
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
    }


    // ====================================
    //   อุ้มสัตว์
    // ====================================
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) return;
        if (onCooldown(player)) return;

        setCooldown(player);

        if (carryingManager.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }

        if (entity instanceof Animals) {
            carryingManager.startCarryingEntity(player, entity);
            event.setCancelled(true);
        }
    }

    // ====================================
    //   อุ้มบล็อก / วาง
    // ====================================
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();

        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) return;
        if (onCooldown(player)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        setCooldown(player);

        // วาง
        if (carryingManager.isCarrying(player)) {

            Location dropLoc = event.getClickedBlock()
                    .getRelative(event.getBlockFace()).getLocation();

            carryingManager.stopCarrying(player, dropLoc);
            event.setCancelled(true);
            return;
        }

        // อุ้มบล็อก
        Material type = event.getClickedBlock().getType();

        if (type.isAir() || type == Material.BEDROCK) return;

        carryingManager.startCarryingBlock(player, event.getClickedBlock());
        event.setCancelled(true);
    }


    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        carryingManager.handlePlayerQuit(event.getPlayer());
    }
}