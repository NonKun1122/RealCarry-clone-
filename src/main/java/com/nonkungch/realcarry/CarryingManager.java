package com.nonkungch.realcarry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CarryingManager {

    private final RealCarry plugin;
    private final Map<UUID, Entity> carryingEntity = new HashMap<>();
    private final Map<UUID, BlockData> carryingBlock = new HashMap<>();
    private final Map<UUID, BlockState> carriedBlockState = new HashMap<>();
    private final Map<UUID, ArmorStand> blockVisual = new HashMap<>();
    private final Map<UUID, ItemStack[]> carriedInventories = new HashMap<>();
    private final Map<UUID, Integer> carryTasks = new HashMap<>();
    private final Map<UUID, Boolean> entityAIState = new HashMap<>();

    public CarryingManager(RealCarry plugin) {
        this.plugin = plugin;
    }

    /**
     * ดึงตัวแปร Plugin หลัก เพื่อใช้เข้าถึง Config ในไฟล์อื่น
     */
    public RealCarry getPlugin() {
        return this.plugin;
    }

    public boolean isCarrying(Player player) {
        UUID id = player.getUniqueId();
        return carryingEntity.containsKey(id) || carryingBlock.containsKey(id);
    }

    /**
     * เริ่ม Task รันทุก Tick เพื่ออัปเดตตำแหน่งสิ่งที่อุ้มให้ตามตัวผู้เล่น
     */
    private void startPositionTask(Player player, Entity target, String type) {
        UUID id = player.getUniqueId();
        double offsetX = plugin.getConfig().getDouble("offsets." + type + ".x", 0);
        double offsetY = plugin.getConfig().getDouble("offsets." + type + ".y", 1.2);
        double offsetZ = plugin.getConfig().getDouble("offsets." + type + ".z", 1.0);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            // หากผู้เล่นออกเกม หรือไม่ได้อุ้มอยู่แล้ว ให้หยุด Task ทันที
            if (!player.isOnline() || !isCarrying(player)) {
                stopTask(id);
                return;
            }

            Location loc = player.getLocation();
            Vector direction = loc.getDirection().setY(0).normalize();
            Vector side = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

            Location targetLoc = loc.clone()
                    .add(direction.multiply(offsetZ))
                    .add(side.multiply(offsetX))
                    .add(0, offsetY, 0);
            
            targetLoc.setYaw(loc.getYaw());
            target.teleport(targetLoc);
        }, 0L, 1L);

        carryTasks.put(id, taskId);
    }

    private void stopTask(UUID id) {
        if (carryTasks.containsKey(id)) {
            Bukkit.getScheduler().cancelTask(carryTasks.remove(id));
        }
    }

    public void startCarryingEntity(Player player, Entity target, String type) {
        UUID id = player.getUniqueId();
        carryingEntity.put(id, target);

        if (target instanceof Mob mob) {
            entityAIState.put(id, mob.hasAI());
            mob.setAI(false); // ปิด AI เพื่อไม่ให้สัตว์ขยับตัวขณะอุ้ม
        }

        startPositionTask(player, target, type);
        applySlow(player);
        player.sendMessage(plugin.getMsg("carrying-entity").replace("%entity%", target.getName()));
    }

    public void startCarryingBlock(Player player, Block block) {
        UUID id = player.getUniqueId();
        BlockState state = block.getState();
        
        // เก็บข้อมูล Snapshot ของ Inventory หากเป็นกล่องหรือเตาเผา
        if (state instanceof Container container) {
            carriedInventories.put(id, container.getSnapshotInventory().getContents());
        }

        carriedBlockState.put(id, state);
        carryingBlock.put(id, block.getBlockData());

        // ทำให้บล็อกหายไปจากโลก (แก้ Ghost Block ด้วยการ Force Update)
        block.setType(Material.AIR, true);

        // สร้าง ArmorStand ล่องหนเพื่อแสดงผลบล็อกที่กำลังอุ้ม
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.getEquipment().setHelmet(new ItemStack(state.getType()));
        
        blockVisual.put(id, stand);
        startPositionTask(player, stand, "block");
        applySlow(player);
        player.sendMessage(plugin.getMsg("carrying-block").replace("%block%", state.getType().name()));
    }

    public void stopCarrying(Player player, Location drop) {
        UUID id = player.getUniqueId();
        stopTask(id);

        if (carryingEntity.containsKey(id)) {
            Entity entity = carryingEntity.remove(id);
            if (entity instanceof Mob mob) {
                mob.setAI(entityAIState.getOrDefault(id, true)); // คืนค่า AI เดิม
            }
            if (entity != null) entity.teleport(drop.clone().add(0.5, 0, 0.5));
        } 
        else if (carryingBlock.containsKey(id)) {
            BlockState state = carriedBlockState.remove(id);
            ArmorStand stand = blockVisual.remove(id);
            ItemStack[] items = carriedInventories.remove(id);

            if (stand != null) stand.remove();
            if (state != null) {
                Block target = drop.getBlock();
                target.setType(state.getType(), true);
                target.setBlockData(state.getBlockData(), true);
                
                // คืนค่าไอเทมในกล่อง
                if (items != null && target.getState() instanceof Container container) {
                    container.getInventory().setContents(items);
                }
            }
            carryingBlock.remove(id);
        }

        removeSlow(player);
        player.sendMessage(plugin.getMsg("placed-object"));
    }

    private void applySlow(Player player) {
        int lv = plugin.getConfig().getInt("slowness-level", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, lv, true, false));
    }

    private void removeSlow(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    public void handlePlayerQuit(Player player) {
        if (isCarrying(player)) stopCarrying(player, player.getLocation().add(0, 0.5, 0));
    }

    public void clearAllCarrying() {
        for (UUID id : new HashMap<>(carryTasks).keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopCarrying(p, p.getLocation());
        }
    }
}
