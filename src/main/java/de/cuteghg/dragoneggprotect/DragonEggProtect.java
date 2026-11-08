package de.cuteghg.dragoneggprotect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DragonEggProtect extends JavaPlugin implements Listener {
    private final Map<UUID, Location> protectedDrops = new HashMap<>();
    private final Set<UUID> restoring = new HashSet<>();
    private BukkitTask particleTask;
    private Location fallbackLocation;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        loadFallback();
        particleTask = Bukkit.getScheduler().runTaskTimer(this, this::tickProtectedDrops, 1L, 5L);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && isDragonEgg(item.getItemStack())) protectDrop(item);
            }
        }
        getLogger().info("DragonEggProtect enabled.");
    }

    @Override
    public void onDisable() {
        if (particleTask != null) particleTask.cancel();
        saveFallback();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isDragonEgg(event.getItemDrop().getItemStack())) protectDrop(event.getItemDrop());
    }

    private void protectDrop(Item item) {
        item.setGravity(false);
        item.setInvulnerable(true);
        item.setPersistent(true);
        item.setVelocity(new Vector(0, 0, 0));
        item.setFireTicks(0);
        protectedDrops.put(item.getUniqueId(), item.getLocation().clone());
        if (fallbackLocation == null) {
            World end = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
            fallbackLocation = end != null ? new Location(end, 0, 64, 0) : item.getLocation().clone();
            saveFallback();
        }
    }

    private void tickProtectedDrops() {
        for (UUID id : new HashSet<>(protectedDrops.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Item item) || !item.isValid()) {
                protectedDrops.remove(id);
                continue;
            }
            Location anchor = protectedDrops.get(id);
            if (anchor == null) continue;
            if (!item.getWorld().equals(anchor.getWorld()) || item.getLocation().distanceSquared(anchor) > 0.04) {
                item.teleport(anchor);
            }
            item.setVelocity(new Vector(0, 0, 0));
            item.setFireTicks(0);
            item.getWorld().spawnParticle(Particle.PORTAL, anchor.clone().add(0, 0.35, 0), 3, 0.22, 0.25, 0.22, 0.02);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && (isDragonEgg(item.getItemStack()) || protectedDrops.containsKey(item.getUniqueId()))) {
            event.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (isDragonEgg(event.getEntity().getItemStack()) || protectedDrops.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            protectDrop(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (isDragonEgg(event.getEntity().getItemStack()) || isDragonEgg(event.getTarget().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isDragonEgg(event.getItem().getItemStack())) protectedDrops.remove(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
            if (fallbackLocation == null) {
                World end = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
                fallbackLocation = end != null ? new Location(end, 0, 64, 0) : event.getBlock().getLocation().clone();
                saveFallback();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.DRAGON_EGG);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.DRAGON_EGG);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ItemStack[] contents = player.getInventory().getContents();
        boolean hadEgg = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isDragonEgg(stack)) {
                hadEgg = true;
                contents[i] = null;
            }
        }
        if (!hadEgg) return;
        player.getInventory().setContents(contents);
        Location placeAt = player.getLocation().getBlock().getLocation();
        Block block = placeAt.getBlock();
        if (block.getType().isAir() || block.isPassable()) {
            block.setType(Material.DRAGON_EGG, false);
            if (fallbackLocation == null) {
                fallbackLocation = placeAt.clone();
                saveFallback();
            }
        } else {
            Item drop = player.getWorld().dropItem(placeAt.add(0.5, 0.2, 0.5), new ItemStack(Material.DRAGON_EGG));
            protectDrop(drop);
        }
    }

    private void rememberAndRestore(Location location) {
        if (fallbackLocation == null) {
            fallbackLocation = location.clone();
            saveFallback();
        }
        Location target = fallbackLocation.clone();
        Bukkit.getScheduler().runTask(this, () -> {
            if (target.getWorld() == null) return;
            Block block = target.getBlock();
            if (block.getType().isAir() || block.getType() == Material.FIRE || block.isPassable()) {
                block.setType(Material.DRAGON_EGG, false);
            } else {
                Item item = target.getWorld().dropItem(target.clone().add(0.5, 0.2, 0.5), new ItemStack(Material.DRAGON_EGG));
                protectDrop(item);
            }
        });
    }

    private void loadFallback() {
        String worldName = getConfig().getString("fallback.world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        fallbackLocation = new Location(world, getConfig().getDouble("fallback.x"), getConfig().getDouble("fallback.y"), getConfig().getDouble("fallback.z"));
    }

    private void saveFallback() {
        if (fallbackLocation == null || fallbackLocation.getWorld() == null) return;
        getConfig().set("fallback.world", fallbackLocation.getWorld().getName());
        getConfig().set("fallback.x", fallbackLocation.getX());
        getConfig().set("fallback.y", fallbackLocation.getY());
        getConfig().set("fallback.z", fallbackLocation.getZ());
        saveConfig();
    }

    private boolean isDragonEgg(ItemStack stack) {
        return stack != null && stack.getType() == Material.DRAGON_EGG;
    }
}
