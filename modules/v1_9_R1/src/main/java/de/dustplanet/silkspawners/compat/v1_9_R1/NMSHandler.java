package de.dustplanet.silkspawners.compat.v1_9_R1;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.craftbukkit.v1_9_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R1.block.CraftCreatureSpawner;
import org.bukkit.craftbukkit.v1_9_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import de.dustplanet.silkspawners.compat.api.NMSProvider;
import net.minecraft.server.v1_9_R1.Entity;
import net.minecraft.server.v1_9_R1.EntityTypes;
import net.minecraft.server.v1_9_R1.Item;
import net.minecraft.server.v1_9_R1.NBTTagCompound;
import net.minecraft.server.v1_9_R1.NBTTagList;
import net.minecraft.server.v1_9_R1.TileEntityMobSpawner;
import net.minecraft.server.v1_9_R1.World;

public class NMSHandler implements NMSProvider {
    private Field tileField;

    public NMSHandler() {
        try {
            // Get the spawner field
            // TODO Needs 1.9 source
            tileField = CraftCreatureSpawner.class.getDeclaredField("spawner");
            tileField.setAccessible(true);
        } catch (SecurityException | NoSuchFieldException e) {
            Bukkit.getServer().getLogger().info("Reflection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void spawnEntity(org.bukkit.World w, short entityID, double x, double y, double z) {
        // TODO Needs 1.9 source
        World world = ((CraftWorld) w).getHandle();
        Entity entity = EntityTypes.a(entityID, world);
        // Should actually never happen since the method above
        // contains a null check, too
        if (entity == null) {
            Bukkit.getLogger().warning("Failed to spawn, falling through. You should report this (entity == null)!");
            return;
        }

        // Random facing
        entity.setPositionRotation(x, y, z, world.random.nextFloat() * 360.0f, 0.0f);
        // We need to add the entity to the world, reason is of
        // course a spawn egg so that other events can handle this
        world.addEntity(entity, SpawnReason.SPAWNER_EGG);
    }

    @Override
    public SortedMap<Integer, String> rawEntityMap() {
        SortedMap<Integer, String> sortedMap = new TreeMap<>();
        // Use reflection to dump native EntityTypes
        // This bypasses Bukkit's wrappers, so it works with mods
        try {
            // TODO Needs 1.9 source
            // g.put(s, Integer.valueOf(i)); --> Name of ID
            Field field = EntityTypes.class.getDeclaredField("g");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) field.get(null);
            // For each entry in our name -- ID map but it into the sortedMap
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                sortedMap.put(entry.getValue(), entry.getKey());
            }
        } catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getServer().getLogger().severe("Failed to dump entity map: " + e.getMessage());
            e.printStackTrace();
        }
        return sortedMap;
    }

    @Override
    public String getMobNameOfSpawner(BlockState blockState) {
        // Get our spawner;
        CraftCreatureSpawner spawner = (CraftCreatureSpawner) blockState;
        // Get the mob ID ourselves if we can
        try {
            TileEntityMobSpawner tile = (TileEntityMobSpawner) tileField.get(spawner);
            // Get the name from the field of our spawner
            return tile.getSpawner().getMobName();
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getServer().getLogger().info("Reflection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void setSpawnersUnstackable() {
        // http://forums.bukkit.org/threads/setting-max-stack-size.66364/
        Item.getById(52).d(1);
    }

    @Override
    public boolean setMobNameOfSpawner(BlockState blockState, String mobID) {
        // Get out spawner;
        CraftCreatureSpawner spawner = (CraftCreatureSpawner) blockState;

        try {
            // Refer to the NMS TileEntityMobSpawner and change the name, see
            // TODO Needs 1.9 source
            TileEntityMobSpawner tile = (TileEntityMobSpawner) tileField.get(spawner);
            // Changes as of 1.7.10
            // TODO Needs 1.9 source
            tile.getSpawner().setMobName(mobID);
            return true;
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getServer().getLogger().info("Reflection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public org.bukkit.entity.Entity getTNTSource(TNTPrimed tnt) {
        return tnt.getSource();
    }

    @Override
    public ItemStack setNBTEntityID(ItemStack item, short entityID, String entity) {
        net.minecraft.server.v1_9_R1.ItemStack itemStack = null;
        CraftItemStack craftStack = CraftItemStack.asCraftCopy(item);
        itemStack = CraftItemStack.asNMSCopy(craftStack);
        NBTTagCompound tag = itemStack.getTag();

        // Create tag if necessary
        if (tag == null) {
            tag = new NBTTagCompound();
            itemStack.setTag(tag);
        }

        // Check for SilkSpawners key
        if (!tag.hasKey("SilkSpawners")) {
            tag.set("SilkSpawners", new NBTTagCompound());
        }
        tag.getCompound("SilkSpawners").setShort("entityID", entityID);

        // Check for Vanilla keys
        if (!tag.hasKey("BlockEntityTag")) {
            tag.set("BlockEntityTag", new NBTTagCompound());
        }

        // EntityId - Deprecated in 1.9
        tag.getCompound("BlockEntityTag").setString("EntityId", entity);

        // SpawnData
        if (!tag.hasKey("SpawnData")) {
            tag.set("SpawnData", new NBTTagCompound());
        }
        tag.getCompound("SpawnData").setString("id", entity);

        if (!tag.getCompound("BlockEntityTag").hasKey("SpawnPotentials")) {
            tag.getCompound("BlockEntityTag").set("SpawnPotentials", new NBTTagCompound());
        }

        NBTTagList tagList = new NBTTagList();
        NBTTagCompound spawnPotentials = new NBTTagCompound();
        spawnPotentials.set("Entity", new NBTTagCompound());
        spawnPotentials.getCompound("Entity").setString("id", entity);
        spawnPotentials.setInt("Weight", 1);
        tagList.add(spawnPotentials);
        tag.getCompound("BlockEntityTag").set("SpawnPotentials", tagList);

        // SpawnEgg data
        if (!tag.hasKey("EntityTag")) {
            tag.set("EntityTag", new NBTTagCompound());
        }
        tag.getCompound("EntityTag").setString("id", entity);

        return CraftItemStack.asCraftMirror(itemStack);
    }

    @Override
    public short getSilkSpawnersNBTEntityID(ItemStack item) {
        net.minecraft.server.v1_9_R1.ItemStack itemStack = null;
        CraftItemStack craftStack = CraftItemStack.asCraftCopy(item);
        itemStack = CraftItemStack.asNMSCopy(craftStack);
        NBTTagCompound tag = itemStack.getTag();

        if (tag == null || !tag.hasKey("SilkSpawners")) {
            return 0;
        }
        return tag.getCompound("SilkSpawners").getShort("entityID");
    }

    @Override
    public String getVanillaNBTEntityID(ItemStack item) {
        net.minecraft.server.v1_9_R1.ItemStack itemStack = null;
        CraftItemStack craftStack = CraftItemStack.asCraftCopy(item);
        itemStack = CraftItemStack.asNMSCopy(craftStack);
        NBTTagCompound tag = itemStack.getTag();

        if (tag == null || !tag.hasKey("BlockEntityTag")) {
            return null;
        }

        tag = tag.getCompound("BlockEntityTag");
        if (tag.hasKey("EntityId")) {
            return tag.getString("EntityId");
        } else if (tag.hasKey("SpawnData") && tag.getCompound("SpawnData").hasKey("id")) {
            return tag.getCompound("SpawnData").getString("id");
        } else if (tag.hasKey("SpawnPotentials") && !tag.getList("SpawnPotentials", 8).isEmpty()) {
            return tag.getList("SpawnPotentials", 8).get(0).getCompound("Entity").getString("id");
        } else {
            return null;
        }
    }

    /**
     * Return the spawner block the player is looking at, or null if isn't.
     * @param player the player
     * @param distance the reach distance
     * @return the found block or null
     */
    @Override
    public Block getSpawnerFacing(Player player, int distance) {
        Block block = player.getTargetBlock((Set<Material>) null, distance);
        if (block == null || block.getType() != Material.MOB_SPAWNER) {
            return null;
        }
        return block;
    }

    @Override
    public Collection<? extends Player> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers();
    }

    @Override
    public ItemStack newEggItem(short entityID, String entity, int amount) {
        ItemStack item = new ItemStack(Material.MONSTER_EGG, amount, entityID);
        net.minecraft.server.v1_9_R1.ItemStack itemStack = null;
        CraftItemStack craftStack = CraftItemStack.asCraftCopy(item);
        itemStack = CraftItemStack.asNMSCopy(craftStack);
        NBTTagCompound tag = itemStack.getTag();

        // Create tag if necessary
        if (tag == null) {
            tag = new NBTTagCompound();
            itemStack.setTag(tag);
        }

        if (!tag.hasKey("EntityTag")) {
            tag.set("EntityTag", new NBTTagCompound());
        }
        tag.getCompound("EntityTag").setString("id", entity);

        return CraftItemStack.asCraftMirror(itemStack);
    }

    @Override
    public String getVanillaEggNBTEntityID(ItemStack item) {
        net.minecraft.server.v1_9_R1.ItemStack itemStack = null;
        CraftItemStack craftStack = CraftItemStack.asCraftCopy(item);
        itemStack = CraftItemStack.asNMSCopy(craftStack);
        NBTTagCompound tag = itemStack.getTag();

        if (tag == null || !tag.hasKey("EntityTag")) {
            return null;
        }

        tag = tag.getCompound("EntityTag");
        if (tag.hasKey("id")) {
            return tag.getString("id");
        }
        return null;
    }

    @Override
    public void displayBossBar(String title, String colorName, String styleName, Player player, Plugin plugin, int period) {
        BarColor color = BarColor.valueOf(colorName.toUpperCase());
        BarStyle style = BarStyle.valueOf(styleName.toUpperCase());
        final BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.addPlayer(player);
        bar.setVisible(true);
        final double interval = 1.0 / (period * 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                double progress = bar.getProgress();
                double newProgress = progress - interval;
                if (progress <= 0.0 || newProgress <= 0.0) {
                    bar.setVisible(false);
                    bar.removeAll();
                    this.cancel();
                } else {
                    bar.setProgress(newProgress);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0, 1L);
    }

    @Override
    public Player getPlayer(String playerUUIDOrName) {
        try {
            // Try if the String could be an UUID
            UUID playerUUID = UUID.fromString(playerUUIDOrName);
            return Bukkit.getPlayer(playerUUID);
        } catch (IllegalArgumentException e) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().equalsIgnoreCase(playerUUIDOrName)) {
                    return onlinePlayer;
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack getItemInHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    @Override
    public void reduceEggs(Player player) {
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        ItemStack itemInOffHand = player.getInventory().getItemInOffHand();
        ItemStack eggs;
        if (itemInMainHand.getType() == Material.MONSTER_EGG) {
            eggs = itemInMainHand;
            // Make it empty
            if (eggs.getAmount() == 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                // Reduce egg
                eggs.setAmount(eggs.getAmount() - 1);
                player.getInventory().setItemInMainHand(eggs);
            }
        } else {
            eggs = itemInOffHand;
            // Make it empty
            if (eggs.getAmount() == 1) {
                player.getInventory().setItemInOffHand(null);
            } else {
                // Reduce egg
                eggs.setAmount(eggs.getAmount() - 1);
                player.getInventory().setItemInOffHand(eggs);
            }
        }
    }

    @Override
    public ItemStack getSpawnerItemInHand(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();
        if ((mainHand.getType() == Material.MONSTER_EGG || mainHand.getType() == Material.MOB_SPAWNER)
                && (offHand.getType() == Material.MONSTER_EGG || offHand.getType() == Material.MOB_SPAWNER)) {
            return null; // not determinable
        } else if (mainHand.getType() == Material.MONSTER_EGG || mainHand.getType() == Material.MOB_SPAWNER) {
            return mainHand;
        } else if (offHand.getType() == Material.MONSTER_EGG || offHand.getType() == Material.MOB_SPAWNER) {
            return offHand;
        }
        return null;
    }

    @Override
    public void setSpawnerItemInHand(Player player, ItemStack newItem) {
        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();
        if ((mainHand.getType() == Material.MONSTER_EGG || mainHand.getType() == Material.MOB_SPAWNER)
                && (offHand.getType() == Material.MONSTER_EGG || offHand.getType() == Material.MOB_SPAWNER)) {
            return; // not determinable
        } else if (mainHand.getType() == Material.MONSTER_EGG || mainHand.getType() == Material.MOB_SPAWNER) {
            inv.setItemInMainHand(newItem);
        } else if (offHand.getType() == Material.MONSTER_EGG || offHand.getType() == Material.MOB_SPAWNER) {
            inv.setItemInOffHand(newItem);
        }
    }
}
