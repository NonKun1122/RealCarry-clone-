package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class CarryingManager {

    private final RealCarry plugin;

    private final HashMap<UUID, Entity> carryingEntity = new HashMap<>();
    private final HashMap<UUID, BlockData> carryingBlock = new HashMap<>();
    private final HashMap<UUID, ArmorStand> blockVisual = new HashMap<>();

    public CarryingManager(RealCarry plugin) {
        this.plugin = plugin;
    }

    public boolean isCarrying(Player player) {
        UUID uuid = player.getUniqueId();
        return carryingEntity.containsKey(uuid) || carryingBlock.containsKey(uuid);
    }

    // ============================
    //     อุ้มสัตว์
    // ============================
    public void startCarryingEntity(Player player, Entity entity) {
        player.addPassenger(entity);
        carryingEntity.put(player.getUniqueId(), entity);

        applySlowEffect(player);
        player.sendMessage(plugin.getMsg("carrying-entity").replace("%entity%", entity.getName()));
    }

    // ============================
    //      อุ้มบล็อก
    // ============================
    public void startCarryingBlock(Player player, Block block) {

        BlockData data = block.getBlockData();
        Material type = block.getType();
        carryingBlock.put(player.getUniqueId(), data);

        // ลบบล็อกจากโลก
        block.setType(Material.AIR);

        Location spawnLoc = player.getLocation().add(0, 0.01, 0);
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);

        // สำคัญมาก — ป้องกัน hitbox ชนหลายผู้เล่น
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setCollidable(false);

        stand.getEquipment().setHelmet(new ItemStack(type));
        stand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

        // ผูก ArmorStand กับผู้เล่น
        player.addPassenger(stand);
        blockVisual.put(player.getUniqueId(), stand);

        applySlowEffect(player);
        player.sendMessage(plugin.getMsg("carrying-block").replace("%block%", type.name()));
    }

    // ============================
    //          วาง
    // ============================
    public void stopCarrying(Player player, Location dropLocation) {

        UUID uuid = player.getUniqueId();

        // วางสัตว์
        if (carryingEntity.containsKey(uuid)) {

            Entity entity = carryingEntity.remove(uuid);

            if (entity != null && player.getPassengers().contains(entity)) {
                player.removePassenger(entity);
            }

            if (entity != null && entity.isValid()) {
                Location loc = dropLocation.clone().add(0.5, 0, 0.5);
                entity.teleport(loc);
            }
        }

        // วางบล็อก
        else if (carryingBlock.containsKey(uuid)) {

            BlockData data = carryingBlock.remove(uuid);
            ArmorStand stand = blockVisual.remove(uuid);

            if (stand != null) {
                if (player.getPassengers().contains(stand)) {
                    player.removePassenger(stand);
                }
                stand.remove();
            }

            if (data != null) {
                dropLocation.getBlock().setBlockData(data);
            }
        }

        removeSlowEffect(player);
        player.sendMessage(plugin.getMsg("placed-object"));
    }

    // ============================
    //    Effect
    // ============================
    private void applySlowEffect(Player player) {
        int level = plugin.getConfig().getInt("slowness-level", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, level, true, false));
    }

    private void removeSlowEffect(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ============================
    //   Cleanup
    // ============================
    public void handlePlayerQuit(Player player) {
        if (isCarrying(player)) {
            stopCarrying(player, player.getLocation().add(0, 0.5, 0));
        }
    }

    public void clearAllCarrying() {
        for (UUID uuid : new HashMap<>(carryingEntity).keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation().add(0, 0.5, 0));
        }
        for (UUID uuid : new HashMap<>(carryingBlock).keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation().add(0, 0.5, 0));
        }
    }
}