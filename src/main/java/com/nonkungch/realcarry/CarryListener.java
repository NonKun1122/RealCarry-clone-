package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class CarryListener implements Listener {

    private final CarryingManager manager;
    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    public CarryListener(RealCarry plugin, CarryingManager manager) {
        this.manager = manager;
    }

    // แก้บัค: ปล่อยของทันทีเมื่อผู้เล่นตาย เพื่อไม่ให้ Entity ค้างกลางอากาศ
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (manager.isCarrying(player)) {
            manager.stopCarrying(player, player.getLocation());
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (manager.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }

        if (!player.isSneaking() || onCooldown(player) || !isHandEmpty(player)) return;

        setCooldown(player);

        if (entity instanceof Player) {
            manager.startCarryingEntity(player, entity, "player");
        } else if (entity.getType().isAlive()) {
            // ระบบ Blacklist: เช็ครายชื่อที่ห้ามอุ้ม
            List<String> forbidden = manager.getPlugin().getConfig().getStringList("forbidden-mobs");
            if (forbidden.contains(entity.getType().name())) {
                player.sendMessage(manager.getPlugin().getMsg("cannot-carry"));
                return;
            }
            manager.startCarryingEntity(player, entity, "mob");
        }
        
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (manager.isCarrying(player)) {
            if (!player.isSneaking()) return;
            
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                if (onCooldown(player)) return;
                setCooldown(player);

                Location dropLoc = (event.getClickedBlock() != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) ? 
                    event.getClickedBlock().getRelative(event.getBlockFace()).getLocation() : 
                    player.getLocation().getBlock().getLocation();

                manager.stopCarrying(player, dropLoc);
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && isHandEmpty(player)) {
            Material type = event.getClickedBlock().getType();
            if (type.isAir() || type == Material.BEDROCK) return;
            setCooldown(player);
            manager.startCarryingBlock(player, event.getClickedBlock());
            event.setCancelled(true);
        }
    }

    private boolean onCooldown(Player p) {
        return System.currentTimeMillis() - cooldown.getOrDefault(p.getUniqueId(), 0L) < 200;
    }

    private void setCooldown(Player p) {
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isHandEmpty(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item == null || item.getType() == Material.AIR;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
        cooldown.remove(event.getPlayer().getUniqueId());
    }
}
