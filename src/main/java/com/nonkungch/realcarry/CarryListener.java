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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
        return now - last < 200; // ป้องกันการคลิกเบิ้ล
    }

    private void setCooldown(Player p) {
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isHandEmpty(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item == null || item.getType() == Material.AIR;
    }

    // ===========================================================
    // 1. จัดการการอุ้ม/วาง Entity (ผู้เล่น และ มอนสเตอร์)
    // ===========================================================
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // ตรวจสอบมือหลักเท่านั้น
        if (event.getHand() != EquipmentSlot.HAND) return;

        // ถ้ากำลังอุ้มอยู่แล้ว ให้ยกเลิกการคลิก Entity อื่น
        if (manager.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }

        // ต้องกด Shift และมือเปล่า
        if (!player.isSneaking()) return;
        if (onCooldown(player)) return;
        if (!isHandEmpty(player)) return;

        setCooldown(player);

        // แยกประเภทเพื่อใช้ Offset ใน Config
        if (entity instanceof Player) {
            manager.startCarryingEntity(player, entity, "player");
        } else if (entity.getType().isAlive()) {
            manager.startCarryingEntity(player, entity, "mob");
        }
        
        event.setCancelled(true);
    }

    // ===========================================================
    // 2. จัดการการอุ้ม/วาง บล็อก (รวมถึงกล่อง และเตาเผา)
    // ===========================================================
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // ตรวจสอบมือหลัก
        if (event.getHand() != EquipmentSlot.HAND) return;

        // ----- กรณีที่ 1: กำลังอุ้มอยู่ (ต้องการวาง) -----
        if (manager.isCarrying(player)) {
            // ต้องกด Shift เพื่อวาง
            if (!player.isSneaking()) return;
            
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                if (onCooldown(player)) return;
                setCooldown(player);

                Location dropLoc;
                if (event.getClickedBlock() != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    // วางบนหน้าบล็อกที่คลิก
                    dropLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                } else {
                    // วางที่เท้าผู้เล่น (กรณีคลิกอากาศ)
                    dropLoc = player.getLocation().getBlock().getLocation();
                }

                manager.stopCarrying(player, dropLoc);
                event.setCancelled(true);
            }
            return;
        }

        // ----- กรณีที่ 2: ไม่ได้อุ้ม (ต้องการหยิบ) -----
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!player.isSneaking()) return;
            if (onCooldown(player)) return;
            
            // ต้องมือเปล่าถึงจะหยิบได้
            if (!isHandEmpty(player)) return;

            Material type = event.getClickedBlock().getType();
            
            // ป้องกันการหยิบบล็อกอากาศ หรือ Bedrock
            if (type.isAir() || type == Material.BEDROCK) return;

            setCooldown(player);
            manager.startCarryingBlock(player, event.getClickedBlock());
            event.setCancelled(true);
        }
    }

    // ===========================================================
    // 3. จัดการกรณีผู้เล่นออกจากเซิร์ฟเวอร์
    // ===========================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
        cooldown.remove(event.getPlayer().getUniqueId());
    }
}
