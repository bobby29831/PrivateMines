/**
 * MIT License
 * <p>
 * Copyright (c) 2021 - 2022 Kyle Hicks
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.untouchedodin0.privatemines.factory;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.adapter.UnsupportedVersionEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.untouchedodin0.kotlin.mine.storage.MineStorage;
import me.untouchedodin0.kotlin.mine.type.MineType;
import me.untouchedodin0.privatemines.PrivateMines;
import me.untouchedodin0.privatemines.events.PrivateMineCreationEvent;
import me.untouchedodin0.privatemines.iterator.SchematicIterator;
import me.untouchedodin0.privatemines.mine.Mine;
import me.untouchedodin0.privatemines.mine.data.MineData;
import me.untouchedodin0.privatemines.mine.data.MineDataBuilder;
import me.untouchedodin0.privatemines.playershops.Shop;
import me.untouchedodin0.privatemines.playershops.ShopBuilder;
import me.untouchedodin0.privatemines.storage.SchematicStorage;
import me.untouchedodin0.privatemines.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.misc.Task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MineFactory {

    PrivateMines privateMines = PrivateMines.getPrivateMines();
    EditSession editSession;
    Location quarryL;


    /**
     * Creates a mine for the {@link Player} at {@link Location} with {@link MineType}
     *
     * @param player   the player the mine should be created for
     * @param location the location of the mine
     * @param mineType the type of mine to paste
     */
    public void create(Player player, Location location, MineType mineType) {
        Instant start = Instant.now();
        UUID uuid = player.getUniqueId();
        File schematicFile = new File("plugins/PrivateMines/schematics/" + mineType.getFile());
        Mine mine = new Mine(privateMines);
        Map<String, Boolean> flags = mineType.getFlags();
        Map<Material, Double> prices = new HashMap<>();
        Connection connection = privateMines.getSqlite().getSQLConnection();

        Map<Material, Double> materials = mineType.getMaterials();
        if (materials != null) {
            prices.putAll(materials);
        }

        if (!schematicFile.exists()) {
            privateMines.getLogger().warning("Schematic file does not exist: " + schematicFile.getName());
            return;
        }

        Shop shop = new ShopBuilder().setOwner(uuid).setPrices(prices).build();
        String mineRegionName = String.format("mine-%s", player.getUniqueId());
        String fullRegionName = String.format("full-mine-%s", player.getUniqueId());

        ClipboardFormat clipboardFormat = ClipboardFormats.findByFile(schematicFile);
        BlockVector3 vector = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        SchematicStorage storage = privateMines.getSchematicStorage();
        SchematicIterator.MineBlocks mineBlocks = storage.getMineBlocksMap().get(schematicFile);

        Task.asyncDelayed(() -> {
            if (clipboardFormat != null) {
                try (ClipboardReader clipboardReader = clipboardFormat.getReader(new FileInputStream(schematicFile))) {
                    World world = BukkitAdapter.adapt(Objects.requireNonNull(location.getWorld()));
                    if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                        editSession = WorldEdit.getInstance().newEditSessionBuilder().world(world).fastMode(true).build();
                    } else {
                        editSession = WorldEdit.getInstance().newEditSession(world);
                    }
                    LocalSession localSession = new LocalSession();

                    Clipboard clipboard = clipboardReader.read();
                    ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);

//                    mb0|0,50,-150
//                    mb1|13,48,-144
//                    mb2|-12,20,-116
//                    cbo|0,50,-151
//                    min|-30000000,-64,-30000000
//                    loc|763.6692645437984,140.45037494032877,728.8705431310638
//
//                    763,140,729 Sponge
//                    751,110,763 Lower Rails Sponge - mb0 + mb2 -> 763 - 0 +-12 = 751
//                    776,138,735 Upper Rails Sponge - mb0 + mb1 -> 763 - 0 + 13 = 776

                    BlockVector3 lrailsV = vector.subtract(mineBlocks.getSpawnLocation()).add(mineBlocks.getCorner2().add(0, 0, 1));
                    BlockVector3 urailsV = vector.subtract(mineBlocks.getSpawnLocation()).add(mineBlocks.getCorner1().add(0, 0, 1));
                    BlockVector3 quarryV;
                    if (mineBlocks.getQuarryLocation() != null) {
                        quarryV = vector.subtract(mineBlocks.getSpawnLocation()).add(mineBlocks.getQuarryLocation().add(0, 0, 1));
                    } else {
                        quarryV = null;
                    }

                    Location spongeL = new Location(location.getWorld(), vector.getBlockX(), vector.getBlockY(), vector.getBlockZ() + 1);
                    Location lrailsL = new Location(location.getWorld(), lrailsV.getBlockX(), lrailsV.getBlockY(), lrailsV.getBlockZ());
                    Location urailsL = new Location(location.getWorld(), urailsV.getBlockX(), urailsV.getBlockY(), urailsV.getBlockZ());

                    if (quarryV != null) {
                        quarryL = new Location(location.getWorld(), quarryV.getBlockX(), quarryV.getBlockY(), quarryV.getBlockZ());
                    }

                    localSession.setClipboard(clipboardHolder);

                    Operation operation = clipboardHolder.createPaste(editSession).to(vector).ignoreAirBlocks(true).build();

                    try {
                        Operations.complete(operation);
                        editSession.close();
                    } catch (WorldEditException worldEditException) {
                        if (worldEditException.getCause() instanceof UnsupportedVersionEditException) {
                            privateMines.getLogger().warning("WorldEdit version " + WorldEdit.getVersion() + " is not supported," +
                                    "if this issue persists, please try using FastAsyncWorldEdit.");
                            return;
                        }
                        worldEditException.printStackTrace();
                    }

                    Region region = clipboard.getRegion();
                    Region newRegion;

                    BlockVector3 clipboardOffset = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin());
                    Vector3 realTo = vector.toVector3().add(clipboardHolder.getTransform().apply(clipboardOffset.toVector3()));
                    Vector3 max = realTo.add(clipboardHolder.getTransform().apply(region.getMaximumPoint().subtract(region.getMinimumPoint()).toVector3()));
                    RegionSelector regionSelector = new CuboidRegionSelector(world, realTo.toBlockPoint(), max.toBlockPoint());
                    localSession.setRegionSelector(world, regionSelector);
                    regionSelector.learnChanges();

                    //noinspection DanglingJavadoc
                    try {
                        newRegion = regionSelector.getRegion();

                        Location fullMin = BukkitAdapter.adapt(BukkitAdapter.adapt(world), newRegion.getMinimumPoint());
                        Location fullMax = BukkitAdapter.adapt(BukkitAdapter.adapt(world), newRegion.getMaximumPoint());

                        ProtectedCuboidRegion miningWorldGuardRegion = new ProtectedCuboidRegion(mineRegionName, lrailsV, urailsV);
                        ProtectedCuboidRegion fullWorldGuardRegion = new ProtectedCuboidRegion(fullRegionName, newRegion.getMinimumPoint(), newRegion.getMaximumPoint());
                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                        RegionManager regionManager = container.get(world);
                        if (regionManager != null) {
                            regionManager.addRegion(miningWorldGuardRegion);
                            regionManager.addRegion(fullWorldGuardRegion);
                        }

                        /**
                         This sadly has to be called synchronously else it'll throw a
                         {@link java.lang.IllegalStateException}
                         This is due to how WorldGuard handles their flags...
                         @see com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent
                         */
                        Task.syncDelayed(() -> {
                            if (flags != null) {
                                flags.forEach((s, aBoolean) -> {
                                    Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), s);
                                    if (aBoolean) {
                                        try {
                                            Utils.setFlag(miningWorldGuardRegion, flag, "allow");
                                        } catch (InvalidFlagFormat e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        try {
                                            Utils.setFlag(miningWorldGuardRegion, flag, "deny");
                                        } catch (InvalidFlagFormat e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }
                        });

                        MineData mineData = new MineDataBuilder()
                                .setOwner(uuid)
                                .setMinimumMining(lrailsL)
                                .setMaximumMining(urailsL)
                                .setMinimumFullRegion(fullMin)
                                .setMaximumFullRegion(fullMax)
                                .setSpawnLocation(spongeL)
                                .setMineLocation(location)
                                .setMineType(mineType)
                                .setShop(shop)
                                .build();
                        mine.setMineData(mineData);
                        mine.saveMineData(player, mineData);

                        try {
                            PreparedStatement preparedStatement = connection
                                    .prepareStatement("INSERT or IGNORE into privatemines (mineOwner, mineType, mineLocation," +
                                            " corner1, corner2, fullRegionMin, fullRegionMax, spawn, tax, isOpen)" +
                                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
                            preparedStatement.setString(1, uuid.toString());
                            preparedStatement.setString(2, mineType.getName());
                            preparedStatement.setString(3, LocationUtils.toString(location));
                            preparedStatement.setString(4, LocationUtils.toString(lrailsL));
                            preparedStatement.setString(5, LocationUtils.toString(urailsL));
                            preparedStatement.setString(6, LocationUtils.toString(fullMin));
                            preparedStatement.setString(7, LocationUtils.toString(fullMax));
                            preparedStatement.setString(8, LocationUtils.toString(spongeL));
                            preparedStatement.setDouble(9, mineData.getTax());
                            preparedStatement.setBoolean(10, true);
                            preparedStatement.executeUpdate();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (IncompleteRegionException e) {
                        e.printStackTrace();
                    }

                    try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(world).fastMode(true).build()) {
                        BlockVector3 spawn = BlockVector3.at(spongeL.getBlockX(), spongeL.getBlockY(), spongeL.getBlockZ());
                        Pattern pattern = BukkitAdapter.adapt(Material.AIR.createBlockData());
                        session.setBlock(spawn, pattern);
                    }

                    if (privateMines.getMineStorage().hasMine(uuid)) {
                        privateMines.getMineStorage().replaceMine(uuid, mine);
                    } else {
                        privateMines.getMineStorage().addMine(uuid, mine);
                    }

                    mine.resetNoCheck();
                    TextComponent teleportMessage = new TextComponent(ChatColor.GREEN + "Click me to teleport to your mine!");
                    teleportMessage.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/privatemines teleport"));
                    PrivateMineCreationEvent privateMineCreationEvent = new PrivateMineCreationEvent(uuid, mine);

                    Instant finished = Instant.now();
                    Duration creationDuration = Duration.between(start, finished);

                    final long microseconds = TimeUnit.NANOSECONDS.toMillis(creationDuration.toNanos());
                    privateMines.getLogger().info("Mine creation time: " + microseconds + " milliseconds");
                    Task.syncDelayed(() -> {
                        spongeL.getBlock().setType(Material.AIR, false);
//                        if (quarryL != null) {
//                            quarryL.getBlock().setType(Material.AIR, false);
//                        }
                        player.teleport(spongeL);
                        Bukkit.getPluginManager().callEvent(privateMineCreationEvent);
                    });
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }

    public Mine createMine(Player player, Location location, MineType mineType) {
        Instant start = Instant.now();
        UUID uuid = player.getUniqueId();
        File schematicFile = new File("plugins/PrivateMines/schematics/" + mineType.getFile());
        Mine mine = new Mine(privateMines);
        Map<String, Boolean> flags = mineType.getFlags();
        Map<Material, Double> prices = new HashMap<>();

        Map<Material, Double> materials = mineType.getMaterials();
        if (materials != null) {
            prices.putAll(materials);
        }

        Shop shop = new ShopBuilder().setOwner(uuid).setPrices(prices).build();
        String mineRegionName = String.format("mine-%s", player.getUniqueId());
        String fullRegionName = String.format("mine-full-%s", player.getUniqueId());

        ClipboardFormat clipboardFormat = ClipboardFormats.findByFile(schematicFile);
        BlockVector3 vector = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        SchematicStorage storage = privateMines.getSchematicStorage();
        SchematicIterator.MineBlocks mineBlocks = storage.getMineBlocksMap().get(schematicFile);

        Task.asyncDelayed(() -> {
            if (clipboardFormat != null) {
                try (ClipboardReader clipboardReader = clipboardFormat.getReader(new FileInputStream(schematicFile))) {
                    World world = BukkitAdapter.adapt(Objects.requireNonNull(location.getWorld()));
                    if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                        editSession = WorldEdit.getInstance().newEditSessionBuilder().world(world).fastMode(true).build();
                    } else {
                        editSession = WorldEdit.getInstance().newEditSession(world);
                    }
                    LocalSession localSession = new LocalSession();

                    Clipboard clipboard = clipboardReader.read();
                    ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);

//                    mb0|0,50,-150
//                    mb1|13,48,-144
//                    mb2|-12,20,-116
//                    cbo|0,50,-151
//                    min|-30000000,-64,-30000000
//                    loc|763.6692645437984,140.45037494032877,728.8705431310638
//
//                    763,140,729 Sponge
//                    751,110,763 Lower Rails Sponge - mb0 + mb2 -> 763 - 0 +-12 = 751
//                    776,138,735 Upper Rails Sponge - mb0 + mb1 -> 763 - 0 + 13 = 776

                    BlockVector3 lrailsV = vector.subtract(mineBlocks.getSpawnLocation()).add(mineBlocks.getCorner2().add(0, 0, 1));
                    BlockVector3 urailsV = vector.subtract(mineBlocks.getSpawnLocation()).add(mineBlocks.getCorner1().add(0, 0, 1));

                    Location spongeL = new Location(location.getWorld(), vector.getBlockX(), vector.getBlockY(), vector.getBlockZ() + 1);

                    Location lrailsL = new Location(location.getWorld(), lrailsV.getBlockX(), lrailsV.getBlockY(), lrailsV.getBlockZ());
                    Location urailsL = new Location(location.getWorld(), urailsV.getBlockX(), urailsV.getBlockY(), urailsV.getBlockZ());

                    localSession.setClipboard(clipboardHolder);

                    Operation operation = clipboardHolder.createPaste(editSession).to(vector).ignoreAirBlocks(true).build();

                    try {
                        Operations.complete(operation);
                        editSession.close();
                    } catch (WorldEditException worldEditException) {
                        worldEditException.printStackTrace();
                    }

                    Region region = clipboard.getRegion();
                    Region newRegion;

                    BlockVector3 clipboardOffset = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin());
                    Vector3 realTo = vector.toVector3().add(clipboardHolder.getTransform().apply(clipboardOffset.toVector3()));
                    Vector3 max = realTo.add(clipboardHolder.getTransform().apply(region.getMaximumPoint().subtract(region.getMinimumPoint()).toVector3()));
                    RegionSelector regionSelector = new CuboidRegionSelector(world, realTo.toBlockPoint(), max.toBlockPoint());
                    localSession.setRegionSelector(world, regionSelector);
                    regionSelector.learnChanges();

                    //noinspection DanglingJavadoc
                    try {
                        newRegion = regionSelector.getRegion();

                        Location fullMin = BukkitAdapter.adapt(BukkitAdapter.adapt(world), newRegion.getMinimumPoint());
                        Location fullMax = BukkitAdapter.adapt(BukkitAdapter.adapt(world), newRegion.getMaximumPoint());

                        ProtectedCuboidRegion miningWorldGuardRegion = new ProtectedCuboidRegion(mineRegionName, lrailsV, urailsV);
                        ProtectedCuboidRegion fullWorldGuardRegion = new ProtectedCuboidRegion(fullRegionName, newRegion.getMinimumPoint(), newRegion.getMaximumPoint());
                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                        RegionManager regionManager = container.get(world);
                        if (regionManager != null) {
                            regionManager.addRegion(miningWorldGuardRegion);
                            regionManager.addRegion(fullWorldGuardRegion);
                        }

                        /**
                         This sadly has to be called synchronously else it'll throw a
                         {@link java.lang.IllegalStateException}
                         This is due to how WorldGuard handles their flags...
                         @see com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent
                         */
                        Task.syncDelayed(() -> {
                            if (flags != null) {
                                flags.forEach((s, aBoolean) -> {
                                    Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), s);
                                    if (aBoolean) {
                                        try {
                                            Utils.setFlag(miningWorldGuardRegion, flag, "allow");
                                        } catch (InvalidFlagFormat e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        try {
                                            Utils.setFlag(miningWorldGuardRegion, flag, "deny");
                                        } catch (InvalidFlagFormat e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }
                        });

                        MineData mineData = new MineDataBuilder()
                                .setOwner(uuid)
                                .setMinimumMining(lrailsL)
                                .setMaximumMining(urailsL)
                                .setMinimumFullRegion(fullMin)
                                .setMaximumFullRegion(fullMax)
                                .setSpawnLocation(spongeL)
                                .setMineLocation(location)
                                .setMineType(mineType)
                                .setShop(shop)
                                .build();
                        mine.setMineData(mineData);
                        mine.saveMineData(player, mineData);
                    } catch (IncompleteRegionException e) {
                        e.printStackTrace();
                    }

                    try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(world).fastMode(true).build()) {
                        BlockVector3 spawn = BlockVector3.at(spongeL.getBlockX(), spongeL.getBlockY(), spongeL.getBlockZ());
                        Pattern pattern = BukkitAdapter.adapt(Material.AIR.createBlockData());
                        session.setBlock(spawn, pattern);
                    }

                    if (privateMines.getMineStorage().hasMine(uuid)) {
                        privateMines.getMineStorage().replaceMine(uuid, mine);
                    } else {
                        privateMines.getMineStorage().addMine(uuid, mine);
                    }

                    mine.resetNoCheck();
                    TextComponent teleportMessage = new TextComponent(ChatColor.GREEN + "Click me to teleport to your mine!");
                    teleportMessage.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/privatemines teleport"));
                    PrivateMineCreationEvent privateMineCreationEvent = new PrivateMineCreationEvent(uuid, mine);

                    Instant finished = Instant.now();
                    Duration creationDuration = Duration.between(start, finished);

                    final long microseconds = TimeUnit.NANOSECONDS.toMillis(creationDuration.toNanos());
                    privateMines.getLogger().info("Mine creation time: " + microseconds + " milliseconds");
                    Task.syncDelayed(() -> {
                        spongeL.getBlock().setType(Material.AIR, false);

                        player.teleport(spongeL);
                        Bukkit.getPluginManager().callEvent(privateMineCreationEvent);
                    });
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
        return mine;
    }

    public void createUpgraded(UUID uuid, Location location, MineType mineType) {
        MineStorage mineStorage = privateMines.getMineStorage();
        if (mineStorage.hasMine(uuid)) {
            Mine mine = createMine(Objects.requireNonNull(Bukkit.getPlayer(uuid)), location, mineType);
            mineStorage.replaceMine(uuid, mine);
        }
    }
}
