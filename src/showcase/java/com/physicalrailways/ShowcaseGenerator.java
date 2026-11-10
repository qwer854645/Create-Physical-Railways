package com.physicalrailways;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** Opt-in, server-only scene using real production placement and coupling. */
@net.neoforged.fml.common.EventBusSubscriber(modid = PhysicalRailways.ID,
    value = net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
public final class ShowcaseGenerator {
  private int age;
  private boolean failed, existing;
  private VehiclePhysics.Unit locomotive, wagon;
  private CouplerEndpoint rear, front;
  private static final String MARKER = "physicalrailways_showcase_ready";

  private static final ShowcaseGenerator INSTANCE = new ShowcaseGenerator();

  @net.neoforged.bus.api.SubscribeEvent
  public static void serverTick(ServerTickEvent.Post event) {
    if (Boolean.getBoolean("physicalrailways.showcaseWorld")) INSTANCE.tick(event);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (Boolean.getBoolean("physicalrailways.showcaseWorld")) INSTANCE.login(event);
  }

  private void tick(ServerTickEvent.Post event) {
    if (failed || existing) return;
    var level = event.getServer().overworld();
    try {
      age++;
      if (age == 20) {
        for (var entity : level.getAllEntities())
          if (entity.getTags().contains(MARKER)) existing = true;
        if (!existing) scenery(level);
      }
      if (age == 120) {
        locomotive = carriage(level, 0, true);
        wagon = carriage(level, 10, false);
      }
      if (age == 220) {
        var player = FakePlayerFactory.getMinecraft(level);
        rear.link(player);
        front.link(player);
        if (!CouplerConnections.connected(level, rear)) throw new IllegalStateException("Coupling failed");
        var marker = EntityType.MARKER.create(level);
        marker.setPos(0, 66, 0);
        marker.addTag(MARKER);
        level.addFreshEntity(marker);
        level.getServer().saveEverything(false, true, true);
        existing = true;
        LoggerFactory.getLogger("Showcase").info("SHOWCASE_READY: real coupled locomotive and wagon; platform and workshop; localhost:25566");
      }
    } catch (Exception error) {
      failed = true;
      LoggerFactory.getLogger("Showcase").error("SHOWCASE_FAILED at stage {}", age, error);
    }
  }

  private static void set(ServerLevel level, BlockPos pos, BlockState state) {
    level.setBlock(pos, state, 3);
  }

  private static BlockState facing(BlockState state, Direction direction) {
    for (var property : state.getProperties())
      if (property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty p
          && p.getName().equals("facing") && p.getPossibleValues().contains(direction))
        return state.setValue(p, direction);
    return state;
  }

  private static void scenery(ServerLevel level) throws Exception {
    for (int x = -2; x <= 2; x++)
      for (int z = -5; z <= 5; z++) level.setChunkForced(x, z, true);
    level.setDayTime(6000);
    level.setWeatherParameters(1000000, 0, false, false);
    var commands = level.getServer().getCommands();
    var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
    for (var command : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false", "difficulty peaceful", "setworldspawn 10 68 -12"))
      commands.performPrefixedCommand(source, command);
    for (int x = -18; x <= 18; x++) for (int z = -28; z <= 38; z++) {
      set(level, new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState());
      if (Math.abs(x) <= 2) set(level, new BlockPos(x, 64, z), Blocks.GRAVEL.defaultBlockState());
    }
    for (int z = -80; z <= 80; z++) {
      set(level, new BlockPos(0, 64, z), Blocks.STONE.defaultBlockState());
      set(level, new BlockPos(0, 65, z), AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, TrackShape.ZO));
    }
    for (int x = 3; x <= 9; x++) for (int z = -18; z <= 28; z++) {
      set(level, new BlockPos(x, 64, z), Blocks.STONE_BRICKS.defaultBlockState());
      set(level, new BlockPos(x, 65, z), (x == 3 ? Blocks.SMOOTH_QUARTZ : Blocks.SMOOTH_STONE).defaultBlockState());
    }
    for (int z : new int[] {-14, -4, 6, 16, 26}) {
      for (int y = 66; y <= 70; y++) set(level, new BlockPos(8, y, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
      set(level, new BlockPos(7, 70, z), Blocks.LANTERN.defaultBlockState());
      set(level, new BlockPos(8, 71, z), Blocks.COPPER_BLOCK.defaultBlockState());
    }
    for (int x = 6; x <= 11; x++) for (int z = -6; z <= 16; z++)
      set(level, new BlockPos(x, 72, z), Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB.defaultBlockState());
    // Native station and signal behaviours bind to the real generated track graph.
    for (int z : new int[] {-18, 28}) {
      var p = new BlockPos(3, 66, z);
      set(level, p, AllBlocks.TRACK_STATION.getDefaultState());
      var station = (com.simibubi.create.content.trains.station.StationBlockEntity) level.getBlockEntity(p);
      bind(station.edgePoint, new BlockPos(0, 65, z).subtract(p));
    }
    for (int z : new int[] {-24, 34}) {
      var p = new BlockPos(-3, 65, z);
      set(level, p.below(), Blocks.STONE_BRICKS.defaultBlockState());
      set(level, p, AllBlocks.TRACK_SIGNAL.getDefaultState());
      var signal = (com.simibubi.create.content.trains.signal.SignalBlockEntity) level.getBlockEntity(p);
      bind(signal.edgePoint, new BlockPos(0, 65, z).subtract(p));
    }
    // Open workshop display keeps bogey, controls and coupling tool easy to photograph.
    for (int x = -12; x <= -5; x++) for (int z = -7; z <= 7; z++)
      set(level, new BlockPos(x, 64, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
    for (int z : new int[] {-5, 0, 5}) {
      set(level, new BlockPos(-8, 65, z), AllBlocks.ANDESITE_CASING.getDefaultState());
      set(level, new BlockPos(-8, 66, z), z == 0 ? Content.DRIVER.get().defaultBlockState()
          : Content.COUPLER.get().defaultBlockState());
    }
  }

  private static void bind(Object behaviour, BlockPos relative) throws Exception {
    for (var entry : Map.of("targetTrack", (Object) relative, "targetDirection", Direction.AxisDirection.POSITIVE).entrySet()) {
      var field = behaviour.getClass().getDeclaredField(entry.getKey());
      field.setAccessible(true);
      field.set(behaviour, entry.getValue());
    }
  }

  private VehiclePhysics.Unit carriage(ServerLevel level, int z, boolean engine) {
    var player = FakePlayerFactory.getMinecraft(level);
    player.setGameMode(GameType.CREATIVE);
    player.getAbilities().instabuild = true;
    player.setPos(.5, 67, z + .5);
    player.setYRot(0);
    player.setXRot(90);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.BOGIE_ITEM.get()));
    var before = new HashSet<>(VehiclePhysics.cargoUnits(level));
    VehiclePlacement.placeAimed(player);
    var unit = VehiclePhysics.cargoUnits(level).stream().filter(u -> !before.contains(u)).findFirst().orElseThrow();
    var p = unit.mount.getBlockPos();
    unit.mount.braking = true;
    unit.mount.setChanged();
    for (int x = -1; x <= 1; x++) for (int dz = -3; dz <= 3; dz++)
      set(level, p.offset(x, 1, dz), (engine ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS).defaultBlockState());
    set(level, p.above(), facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.DOWN));
    if (engine) {
      for (int dz = -3; dz <= 0; dz++) {
        set(level, p.offset(0, 2, dz), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState());
        set(level, p.offset(0, 3, dz), Blocks.WAXED_CUT_COPPER.defaultBlockState());
      }
      set(level, p.offset(0, 4, -2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
      for (int x : new int[] {-1, 1}) for (int dz = 1; dz <= 3; dz++)
        set(level, p.offset(x, 2, dz), Blocks.DARK_OAK_FENCE.defaultBlockState());
      for (int x = -1; x <= 1; x++) for (int dz = 1; dz <= 3; dz++)
        set(level, p.offset(x, 4, dz), Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB.defaultBlockState());
      set(level, p.offset(0, 2, 2), facing(Content.DRIVER.get().defaultBlockState(), Direction.SOUTH));
      var driver = (DriverConsoleEntity) level.getBlockEntity(p.offset(0, 2, 2));
      driver.placed(level, player);
      for (int x : new int[] {-1, 1})
        set(level, p.offset(x, 2, -3), facing(Blocks.LANTERN.defaultBlockState(), Direction.NORTH));
      set(level, p.offset(0, 2, 4), facing(Content.COUPLER.get().defaultBlockState(), Direction.SOUTH));
      rear = CouplerEndpoint.get(level.getBlockEntity(p.offset(0, 2, 4)));
    } else {
      for (int dz : new int[] {-2, 0, 2}) {
        set(level, p.offset(-1, 2, dz), facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
        set(level, p.offset(1, 2, dz), facing(Blocks.CHEST.defaultBlockState(), Direction.EAST));
      }
      set(level, p.offset(0, 2, 0), AllBlocks.FLUID_TANK.getDefaultState());
      set(level, p.offset(0, 3, 0), AllBlocks.FLUID_TANK.getDefaultState());
      set(level, p.offset(0, 2, -4), facing(Content.COUPLER.get().defaultBlockState(), Direction.NORTH));
      front = CouplerEndpoint.get(level.getBlockEntity(p.offset(0, 2, -4)));
    }
    unit.carrier.buildMassTracker();
    unit.carrier.updateMergedMassData(1);
    SubLevelPhysicsSystem.get(level).getPipeline().onStatsChanged(unit.carrier);
    LoggerFactory.getLogger("Showcase").info("SHOWCASE_CARRIAGE engine={} body={} mount={}", engine, unit.carrier.getUniqueId(), p);
    return unit;
  }

  private void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
    player.setGameMode(GameType.CREATIVE);
    player.connection.teleport(11, 68, -12, 35, 12);
    for (var stack : List.of(new ItemStack(Content.COUPLER_LINK.get()), AllItems.WRENCH.asStack(),
        new ItemStack(Content.BOGIE_ITEM.get()), new ItemStack(Content.DRIVER_ITEM.get()),
        AllItems.GOGGLES.asStack(), AllItems.SCHEDULE.asStack())) player.getInventory().add(stack);
    player.sendSystemMessage(Component.literal("Screenshot scene: train & station (0,65,5); component display (-8,66,0). Use F1 to hide HUD, F2 to capture."));
  }
}
