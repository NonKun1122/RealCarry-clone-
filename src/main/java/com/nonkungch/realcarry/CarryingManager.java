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

    private final HashMap<UUID, Boolean> entityAIState = new HashMap<>();

    public CarryingManager(RealCarry plugin) {
        this.plugin = plugin;
    }

    public boolean isCarrying(Player player) {
        UUID id = player.getUniqueId();
        return carryingEntity.containsKey(id) || carryingBlock.containsKey(id);
    }

    // ================================================================
    //                    Carry Player / Any Mob
    // ================================================================
    public void startCarryingEntity(Player player, Entity target) {

        UUID id = player.getUniqueId();
        carryingEntity.put(id, target);

        if (target instanceof Mob mob) {
            entityAIState.put(id, mob.hasAI());
            mob.setAI(false);
        }

        player.addPassenger(target);

        applySlow(player);
        player.sendMessage(plugin.getMsg("carrying-entity").replace("%entity%", target.getName()));
    }

    // ================================================================
    //                          Carry Block
    // ================================================================
    public void startCarryingBlock(Player player, Block block) {

        UUID id = player.getUniqueId();

        BlockData data = block.getBlockData();
        BlockState state = block.getState();

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
    //                              Place
    // ================================================================
    public void stopCarrying(Player player, Location drop) {

        UUID id = player.getUniqueId();

        // Entity
        if (carryingEntity.containsKey(id)) {

            Entity entity = carryingEntity.remove(id);

            if (entity != null && player.getPassengers().contains(entity))
                player.removePassenger(entity);

            if (entity instanceof Mob mob) {
                boolean oldAI = entityAIState.getOrDefault(id, true);
                mob.setAI(oldAI);
                entityAIState.remove(id);
            }

            if (entity != null && entity.isValid()) {
                entity.teleport(drop.add(0.5, 0, 0.5));
            }
        }

        // Block
        else if (carryingBlock.containsKey(id)) {

            BlockState state = carriedBlockState.remove(id);
            ArmorStand stand = blockVisual.remove(id);

            if (stand != null) {
                if (player.getPassengers().contains(stand))
                    player.removePassenger(stand);
                stand.remove();
            }

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
    //                            Cleanup
    // ================================================================
    public void handlePlayerQuit(Player player) {
        if (isCarrying(player))
            stopCarrying(player, player.getLocation().add(0, 0.5, 0));
    }

    // ================================================================
    //                        clearAllCarrying
    // ================================================================
    public void clearAllCarrying() {

        // Clear entities
        for (UUID uuid : new HashMap<>(carryingEntity).keySet()) {

            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;

            Entity entity = carryingEntity.remove(uuid);

            if (entity instanceof Mob mob) {
                boolean oldAI = entityAIState.getOrDefault(uuid, true);
                mob.setAI(oldAI);
                entityAIState.remove(uuid);
            }

            if (entity != null && player.getPassengers().contains(entity))
                player.removePassenger(entity);

            if (entity != null && entity.isValid()) {
                entity.teleport(player.getLocation().add(0.5, 0, 0.5));
            }
        }

        // Clear blocks
        for (UUID uuid : new HashMap<>(carryingBlock).keySet()) {

            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;

            BlockState state = carriedBlockState.remove(uuid);
            ArmorStand stand = blockVisual.remove(uuid);

            if (stand != null) {
                if (player.getPassengers().contains(stand))
                    player.removePassenger(stand);
                stand.remove();
            }

            if (state != null) {
                Block block = player.getLocation().add(0, 0.5, 0).getBlock();
                block.setType(state.getType(), false);
                state.update(true, false);
            }

            carryingBlock.remove(uuid);
        }
    }
}