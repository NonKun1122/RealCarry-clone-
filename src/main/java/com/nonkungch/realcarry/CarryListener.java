package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
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

    private final CarryingManager manager;

    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    public CarryListener(RealCarry plugin, CarryingManager manager) {
        this.manager = manager;
    }

    private boolean onCooldown(Player p) {
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);
        return now - last < 200;
    }

    private void setCooldown(Player p) {
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // ===============================================================
    //                อุ้ม Player / อุ้มสัตว์ / อุ้มมอนทุกชนิด
    // ===============================================================
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!player.isSneaking()) return;
        if (onCooldown(player)) return;

        setCooldown(player);

        if (manager.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }

        // อุ้มผู้เล่น + อุ้มมอนทุกชนิดที่ "มีชีวิต"
        if (entity.getType().isAlive()) {
            manager.startCarryingEntity(player, entity);
            event.setCancelled(true);
        }
    }

    // ===============================================================
    //                       อุ้มบล็อก / วางบล็อก
    // ===============================================================
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;
        if (onCooldown(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        setCooldown(player);

        // ----- วาง -----
        if (manager.isCarrying(player)) {
            Location drop = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            manager.stopCarrying(player, drop);
            event.setCancelled(true);
            return;
        }

        // ----- อุ้ม -----
        Material type = event.getClickedBlock().getType();
        if (type.isAir() || type == Material.BEDROCK) return;

        manager.startCarryingBlock(player, event.getClickedBlock());
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }
}