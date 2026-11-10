package de.cuteghg.dragoneggprotect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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
    private BukkitTask particleTask;
    private Location fallbackLocation;

    @Override
    public void onEnable() {
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

    // Covers every source of a dropped egg, including the torch-break trick.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isDragonEgg(event.getEntity().getItemStack())) protectDrop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        ItemStack egg = null;
        java.util.Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (isDragonEgg(drop)) {
                egg = drop.clone();
                drops.remove();
                break;
            }
        }
        if (egg == null) return;

        ItemStack droppedEgg = egg;
        Location deathLocation = event.getEntity().getLocation().clone();
        Bukkit.getScheduler().runTask(this, () -> {
            Item item = deathLocation.getWorld().dropItem(deathLocation, droppedEgg);
            protectDrop(item);
        });
    }

    private void protectDrop(Item item) {
        item.setGravity(false);
        item.setInvulnerable(true);
        item.setPersistent(true);
        item.setGlowing(true);
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
            item.setGlowing(true);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEggPlaced(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.DRAGON_EGG) {
            Location loc = event.getBlockPlaced().getLocation();
            getConfig().set("egg.lastStatus", "placed");
            getConfig().set("egg.world", loc.getWorld().getName());
            getConfig().set("egg.x", loc.getBlockX());
            getConfig().set("egg.y", loc.getBlockY());
            getConfig().set("egg.z", loc.getBlockZ());
            getConfig().set("egg.owner", event.getPlayer().getName());
            saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
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
            if (isDragonEgg(contents[i])) {
                hadEgg = true;
                contents[i] = null;
            }
        }
        if (!hadEgg) return;

        player.getInventory().setContents(contents);
        Location placeAt = player.getLocation().getBlock().getLocation();
        Block block = placeAt.getBlock();
        if (block.getType().isAir() || block.isPassable()) {
            BlockState replacedState = block.getState();
            block.setType(Material.DRAGON_EGG, false);
            // Notify other plugins that this egg was placed by this player.
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, replacedState, block.getRelative(0, -1, 0),
                    new ItemStack(Material.DRAGON_EGG), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (fallbackLocation == null) {
                fallbackLocation = placeAt.clone();
                saveFallback();
            }
        } else {
            Item drop = player.getWorld().dropItem(placeAt.add(0.5, 0.2, 0.5), new ItemStack(Material.DRAGON_EGG));
            protectDrop(drop);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dragonegg")) return false;
        if (!sender.hasPermission("dragoneggprotect.where")) {
            sender.sendMessage("§cDazu hast du keine Berechtigung.");
            return true;
        }

        // Check online inventories first, so the current holder is reported accurately.
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (isDragonEgg(stack)) {
                    sender.sendMessage("§6Drachenei: §eIm Inventar von §f" + player.getName());
                    sender.sendMessage("§7Position: §f" + formatLocation(player.getLocation()));
                    return true;
                }
            }
            if (isDragonEgg(player.getInventory().getItemInOffHand())) {
                sender.sendMessage("§6Drachenei: §eIm Inventar von §f" + player.getName());
                sender.sendMessage("§7Position: §f" + formatLocation(player.getLocation()));
                return true;
            }
        }

        // A dropped egg also has a precise location.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && isDragonEgg(item.getItemStack())) {
                    sender.sendMessage("§6Drachenei: §eAls Item fallengelassen");
                    sender.sendMessage("§7Position: §f" + formatLocation(item.getLocation()));
                    return true;
                }
            }
        }

        // Search already-loaded chunks only; this does not load chunks or cause world generation.
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block.getType() == Material.DRAGON_EGG) {
                                sender.sendMessage("§6Drachenei: §aPlatziert");
                                sender.sendMessage("§7Position: §f" + formatLocation(block.getLocation()));
                                return true;
                            }
                        }
                    }
                }
            }
        }

        String lastStatus = getConfig().getString("egg.lastStatus", "");
        String owner = getConfig().getString("egg.owner");
        if (lastStatus.equals("placed") && getConfig().getString("egg.world") != null) {
            String world = getConfig().getString("egg.world");
            int x = getConfig().getInt("egg.x");
            int y = getConfig().getInt("egg.y");
            int z = getConfig().getInt("egg.z");
            sender.sendMessage("§6Drachenei: §cAktuell nicht gefunden (zuletzt platziert)");
            sender.sendMessage("§7Letzte Position: §f" + world + " " + x + " " + y + " " + z);
            return true;
        }

        sender.sendMessage("§cDrachenei aktuell nicht gefunden.");
        if (owner != null) sender.sendMessage("§7Zuletzt zugeordnet: §f" + owner);
        return true;
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ();
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
