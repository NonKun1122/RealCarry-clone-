package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
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
    private final HashMap<UUID, BlockState> carriedBlockState = new HashMap<>();
    private final HashMap<UUID, ArmorStand> blockVisual = new HashMap<>();

    // เก็บสถานะ AI เดิมของมอนสเตอร์
    private final HashMap<UUID, Boolean> entityAIState = new HashMap<>();

    public CarryingManager(RealCarry plugin) {
        this.plugin = plugin;
    }

    public boolean isCarrying(Player player) {
        UUID id = player.getUniqueId();
        return carryingEntity.containsKey(id) || carryingBlock.containsKey(id);
    }

    // ================================================================
    //                        อุ้ม Player / มอนทุกชนิด
    // ================================================================
    public void startCarryingEntity(Player player, Entity target) {

        UUID id = player.getUniqueId();
        carryingEntity.put(id, target);

        // ถ้าเป็นมอนสเตอร์ที่โจมตีผู้เล่นได้
        if (target instanceof Mob mob) {

            // เก็บสถานะเดิมของ AI
            entityAIState.put(id, mob.hasAI());

            // ปิด AI — ปิดการโจมตี, ไล่ล่า, มองหาเป้าหมาย
            mob.setAI(false);
        }

        player.addPassenger(target);

        applySlow(player);
        player.sendMessage(plugin.getMsg("carrying-entity").replace("%entity%", target.getName()));
    }

    // ================================================================
    //                               อุ้มบล็อก
    // ================================================================
    public void startCarryingBlock(Player player, Block block) {

        UUID id = player.getUniqueId();

        BlockData data = block.getBlockData();
        BlockState state = block.getState();

        // เก็บ block state เพื่อป้องกันของในกล่องหาย
        carriedBlockState.put(id, state);
        carryingBlock.put(id, data);

        block.setType(Material.AIR);

        Location loc = player.getLocation().add(0, 0.02, 0);
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setCollidable(false);

        stand.getEquipment().setHelmet(new ItemStack(state.getType()));
        stand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

        player.addPassenger(stand);
        blockVisual.put(id, stand);

        applySlow(player);
        player.sendMessage(plugin.getMsg("carrying-block").replace("%block%", state.getType().name()));
    }

    // ================================================================
    //                                วาง
    // ================================================================
    public void stopCarrying(Player player, Location drop) {

        UUID id = player.getUniqueId();

        // ---------------- วาง Entity ----------------
        if (carryingEntity.containsKey(id)) {

            Entity entity = carryingEntity.remove(id);

            // เอา entity ออกจากบนหัว
            if (entity != null && player.getPassengers().contains(entity))
                player.removePassenger(entity);

            // คืน AI ให้เหมือนเดิม
            if (entity instanceof Mob mob) {
                boolean oldAI = entityAIState.getOrDefault(id, true);
                mob.setAI(oldAI);
                entityAIState.remove(id);
            }

            if (entity != null && entity.isValid()) {
                entity.teleport(drop.add(0.5, 0, 0.5));
            }
        }

        // ---------------- วาง Block ----------------
        else if (carryingBlock.containsKey(id)) {

            BlockState state = carriedBlockState.remove(id);
            ArmorStand stand = blockVisual.remove(id);

            if (stand != null) {
                if (player.getPassengers().contains(stand))
                    player.removePassenger(stand);
                stand.remove();
            }

            // คืนบล็อก + ของข้างใน
            if (state != null) {
                Block target = drop.getBlock();
                target.setType(state.getType(), false);
                state.update(true, false);
            }

            carryingBlock.remove(id);
        }

        removeSlow(player);
        player.sendMessage(plugin.getMsg("placed-object"));
    }

    // ================================================================
    //                              Effects
    // ================================================================
    private void applySlow(Player player) {
        int lv = plugin.getConfig().getInt("slowness-level", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, lv, true, false));
    }

    private void removeSlow(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ================================================================
    //                              Cleanup
    // ================================================================
    public void handlePlayerQuit(Player player) {
        if (isCarrying(player))
            stopCarrying(player, player.getLocation().add(0, 0.5, 0));
    }
}