package com.guidedrailways;

import static com.guidedrailways.VehiclePhysics.*;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Construction, whole-carriage rerailing and placement transactions. */
final class VehiclePlacement {
  private static final double PLATE_BOTTOM = BogieGeometry.MOUNT_UNDERSIDE;

  private VehiclePlacement() {}

  private record Relocate(ServerLevel level, UUID bogie, long expires) {}

  private static final Map<UUID, Relocate> RELOCATING = new HashMap<>();

  /**
   * Wrench a derailed vehicle, then wrench the destination track. Same gesture as Create's train
   * relocator.
   */
  static void beginRelocate(net.minecraft.server.level.ServerPlayer player, Unit u) {
    var level = u.bogie.getLevel();
    var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
    if (pipeline.getLinearVelocity(u.carrier, new Vector3d()).length() > .25
        || pipeline.getLinearVelocity(u.bogie, new Vector3d()).length() > .25) {
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable(
              "message.guidedrailways.rail.stop_select"),
          true);
      return;
    }
    var existing = RELOCATING.get(player.getUUID());
    if (existing != null
        && existing.level == level
        && existing.bogie.equals(u.bogie.getUniqueId())
        && existing.expires >= level.getGameTime()) {
      RELOCATING.remove(player.getUUID());
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable(
              "message.guidedrailways.rail.cancelled"),
          true);
      return;
    }
    RELOCATING.put(
        player.getUUID(), new Relocate(level, u.bogie.getUniqueId(), level.getGameTime() + 600));
    player.displayClientMessage(
        net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.selected"),
        true);
  }

  static boolean hasRelocation(net.minecraft.server.level.ServerPlayer player) {
    return RELOCATING.containsKey(player.getUUID());
  }

  /**
   * @return true when this click was a relocate attempt and should not reach the track
   */
  static boolean relocateClick(net.minecraft.server.level.ServerPlayer player) {
    var level = player.serverLevel();
    var pending = RELOCATING.get(player.getUUID());
    if (pending == null) return false;
    if (pending.level != level) {
      RELOCATING.remove(player.getUUID());
      return false;
    }
    if (pending.expires < level.getGameTime()) {
      RELOCATING.remove(player.getUUID());
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.expired"),
          true);
      return true;
    }
    var unit = VehiclePhysics.unit(level, pending.bogie);
    if (unit == null) {
      RELOCATING.remove(player.getUUID());
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable(
              "message.guidedrailways.rail.missing_bogie"),
          true);
      return true;
    }
    var sample = TrackNetwork.pick(player, unit.mount.gauge);
    if (sample == null) {
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.aim"),
          true);
      return true;
    }
    var targetPos =
        BlockPos.containing(sample.position().x, sample.position().y, sample.position().z);
    if (!player.mayBuild() || !level.mayInteract(player, targetPos)) {
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.denied"),
          true);
      return true;
    }
    try {
      reraileCarriage(unit, sample);
      RELOCATING.remove(player.getUUID());
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable(
              "message.guidedrailways.rail.completed"),
          true);
    } catch (IllegalArgumentException | IllegalStateException e) {
      player.displayClientMessage(UserText.error(e), true);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info("[RailPlacement] action=rerail_rejected reason={}", e.getMessage());
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .error("[RailPlacement] rerail failed", e);
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable("message.guidedrailways.rail.failed"),
          true);
    }
    return true;
  }

  /** Place every bogie on the same carrier onto the track around the clicked sample. */
  static void reraileCarriage(Unit selected, TrackNetwork.Sample sample) {
    if (selected.carrier.isRemoved() || selected.bogie.isRemoved() || selected.mount.isRemoved())
      throw new IllegalStateException("message.guidedrailways.rail.missing_body");
    if (CouplerConnections.blocksRelocation(selected.carrier))
      throw new IllegalArgumentException("message.guidedrailways.rail.uncouple");
    var level = selected.bogie.getLevel();
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var units = new ArrayList<Unit>();
    for (var u : VehiclePhysics.cargoUnits(level))
      if (u.carrier == selected.carrier && !u.bogie.isRemoved() && !u.mount.isRemoved())
        units.add(u);
    if (units.isEmpty()) units.add(selected);
    for (var mount : VehiclePhysics.mounts(level))
      if (mount.bogieId != null
          && SableCompanion.INSTANCE.getContaining(level, mount.getBlockPos()) == selected.carrier
          && units.stream().noneMatch(u -> u.mount == mount))
        throw new IllegalStateException("message.guidedrailways.rail.changed");
    units.sort(Comparator.comparing(u -> u.bogie.getUniqueId()));
    int selectedIndex = units.indexOf(selected);
    if (selectedIndex < 0) throw new IllegalStateException("message.guidedrailways.rail.changed");
    var carrierPose = pipe.readPose(selected.carrier, new Pose3d());
    var seats = new ArrayList<Vector3d>();
    for (var u : units)
      seats.add(
          new Vector3d(
                  u.mount.getBlockPos().getX() + .5,
                  u.mount.getBlockPos().getY() + PLATE_BOTTOM,
                  u.mount.getBlockPos().getZ() + .5)
              .sub(u.carrier.logicalPose().rotationPoint()));
    var localForward =
        new Vector3d(0, 0, 1)
            .rotate(pipe.readPose(selected.bogie, new Pose3d()).orientation())
            .rotate(new Quaterniond(carrierPose.orientation()).conjugate());
    localForward.y = 0;
    if (localForward.lengthSquared() < 1e-8) localForward.set(0, 0, 1);
    else localForward.normalize();
    double extent = 4;
    for (int i = 0; i < seats.size(); i++)
      extent =
          Math.max(
              extent,
              seats.get(i).distance(seats.get(selectedIndex)) * 1.8
                  + units.get(i).mount.variant.half * 2
                  + 4);
    if (extent > 256) throw new IllegalArgumentException("message.guidedrailways.rail.too_long");
    var route =
        TrackNetwork.placementRoute(
            level,
            sample,
            extent,
            units.stream().map(u -> u.mount.gauge).collect(java.util.stream.Collectors.toSet()));
    var plan =
        RailPlacement.carriage(
            route,
            seats,
            selectedIndex,
            localForward,
            units.stream()
                .map(
                    u ->
                        new RailPlacement.Dimensions(
                            u.mount.variant.ride, u.mount.variant.half, u.mount.pivotHeight()))
                .toList());
    if (pipe.getLinearVelocity(selected.carrier, new Vector3d()).length() > .25)
      throw new IllegalArgumentException("message.guidedrailways.rail.stop");
    record Move(
        ServerSubLevel body,
        Vector3d position,
        Quaterniond rotation,
        Pose3d before,
        Vector3d velocity,
        Vector3d angular) {}
    var moves = new ArrayList<Move>();
    moves.add(
        new Move(
            selected.carrier,
            plan.position(),
            plan.rotation(),
            carrierPose,
            pipe.getLinearVelocity(selected.carrier, new Vector3d()),
            pipe.getAngularVelocity(selected.carrier, new Vector3d())));
    for (int i = 0; i < units.size(); i++) {
      var u = units.get(i);
      var fit = plan.bogies().get(i);
      checkPlacementSpace(level, fit.origin(), fit.rotation(), u.mount.variant, units);
      var target =
          new Vector3d(u.bogie.logicalPose().rotationPoint())
              .sub(center(u.bogie))
              .rotate(fit.rotation())
              .add(fit.origin());
      moves.add(
          new Move(
              u.bogie,
              target,
              fit.rotation(),
              pipe.readPose(u.bogie, new Pose3d()),
              pipe.getLinearVelocity(u.bogie, new Vector3d()),
              pipe.getAngularVelocity(u.bogie, new Vector3d())));
    }
    MoveBatch.commit(
        moves,
        m -> {
          if (m.body.isRemoved() || !m.position.isFinite() || !m.rotation.isFinite())
            throw new IllegalStateException("message.guidedrailways.body.changed");
          if (!level
              .getWorldBorder()
              .isWithinBounds(BlockPos.containing(m.position.x, m.position.y, m.position.z)))
            throw new IllegalArgumentException("message.guidedrailways.placement.border");
        },
        m -> {
          pipe.teleport(m.body, m.position, m.rotation);
          pipe.resetVelocity(m.body);
          m.body.updateLastPose();
          m.body.updateBoundingBox();
        },
        m -> {
          pipe.teleport(m.body, m.before.position(), m.before.orientation());
          pipe.resetVelocity(m.body);
          pipe.addLinearAndAngularVelocity(m.body, m.velocity, m.angular);
          m.body.updateLastPose();
          m.body.updateBoundingBox();
        });
    for (int i = 0; i < units.size(); i++) {
      var unit = units.get(i);
      finishReraile(unit);
      seedPlacement(unit, plan.bogies().get(i), route);
    }
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[GuideRerailed] carrier={} bogies={} curve={} anchor={}",
            selected.carrier.getUniqueId(),
            units.size(),
            sample.path().edge().isTurn(),
            sample.position());
  }

  private static void finishReraile(Unit u) {
    u.clearGuides();
    u.detached = false;
    u.exposure.curveSeconds = 0;
    u.exposure.impactSeconds = 0;
    u.samples[0] = null;
    u.samples[1] = null;
    u.speed = 0;
    u.curveRisk = 0;
    u.lateralSpeed = 0;
    u.reverseTravel = u.mount.getSpeed() < 0;
    u.derailGraceUntil = u.bogie.getLevel().getGameTime() + 40;
    var tag = u.bogie.getUserDataTag();
    if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
    tag.putBoolean("GuidedDerailed", false);
    u.bogie.setUserDataTag(tag);
  }

  static void place(ServerLevel level, UseOnContext context) {
    var player = (net.minecraft.server.level.ServerPlayer) context.getPlayer();
    if (SubLevelPhysicsSystem.get(level) == null)
      throw new IllegalStateException("message.guidedrailways.placement.no_physics");
    var carrierBody = SableCompanion.INSTANCE.getContaining(level, context.getClickedPos());
    if (carrierBody instanceof ServerSubLevel carrier) {
      installBelow(level, context, carrier);
      return;
    }
    placeAimed(player);
  }

  /** Shared by block-use and air-use: the server picks the visible rail, including Bezier spans. */
  static void placeAimed(net.minecraft.server.level.ServerPlayer player) {
    var level = player.serverLevel();
    if (!player.mayBuild())
      throw new IllegalArgumentException("message.guidedrailways.placement.denied");
    if (SubLevelPhysicsSystem.get(level) == null)
      throw new IllegalStateException("message.guidedrailways.placement.no_physics");
    var selection = PhysicalBogeySelection.selected(player);
    var sample = TrackNetwork.pick(player, null);
    if (sample == null) throw new IllegalArgumentException("message.guidedrailways.rail.aim");
    if (selection.gauge() != null && !sample.path().supports(selection.gauge()))
      throw new IllegalArgumentException("message.guidedrailways.bogey.gauge");
    var gauge =
        selection.gauge() == null
            ? RailGauge.forTrack(sample.path().edge().getTrackMaterial().trackType.id.toString())
            : selection.gauge();
    if (selection.gauge() == null)
      selection =
          new PhysicalBogeySelection.Selection(
              gauge, BogeyVariant.defaultFor(gauge), selection.data());
    var pos = BlockPos.containing(sample.position().x, sample.position().y, sample.position().z);
    if (!level.mayInteract(player, pos))
      throw new IllegalArgumentException("message.guidedrailways.placement.track_denied");
    var route =
        TrackNetwork.placementRoute(
            level, sample, Math.max(4, selection.variant().half * 2 + 2), Set.of(gauge));
    var fit =
        RailPlacement.bogie(
            route,
            0,
            selection.variant().ride,
            selection.variant().half,
            selection.variant().compactPivotHeight());
    checkPlacementSpace(level, fit.origin(), fit.rotation(), selection.variant(), List.of());
    ServerSubLevel bogie = null, carrier = null;
    try {
      bogie =
          createBody(
              level,
              Content.BOGIE
                  .get()
                  .defaultBlockState()
                  .setValue(BogieBlock.STYLE, selection.variant().ordinal()),
              fit.origin(),
              fit.rotation());
      applyBogeySelection(bogie, selection);
      var upperOrigin =
          new Vector3d(fit.origin())
              .add(
                  new Vector3d(0, selection.variant().compactPlateOffset(), 0)
                      .rotate(fit.rotation()));
      carrier =
          createBody(
              level,
              Content.MOUNT
                  .get()
                  .defaultBlockState()
                  .setValue(MountBlock.COMPACT, true)
                  .setValue(BogieBlock.STYLE, selection.variant().ordinal()),
              upperOrigin,
              fit.rotation());
      var mount = (MountEntity) level.getBlockEntity(carrier.getPlot().getCenterBlock());
      if (mount == null)
        throw new IllegalStateException("message.guidedrailways.placement.mount_failed");
      mount.gauge = gauge;
      mount.variant = selection.variant();
      mount.bogieId = bogie.getUniqueId();
      mount.setChanged();
      register(mount);
      tick(level);
      var u = VehiclePhysics.unit(level, bogie.getUniqueId());
      if (u != null) seedPlacement(u, fit, route);
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
          .info(
              "[RailPlacement] action=place curve={} position={} frontArc={} rearArc={}",
              sample.path().edge().isTurn(),
              fit.origin(),
              fit.front(),
              fit.rear());
    } catch (RuntimeException e) {
      var c = SubLevelContainer.getContainer(level);
      if (bogie != null && !bogie.isRemoved())
        c.removeSubLevel(bogie, SubLevelRemovalReason.REMOVED);
      if (carrier != null && !carrier.isRemoved())
        c.removeSubLevel(carrier, SubLevelRemovalReason.REMOVED);
      throw e;
    }
  }

  /** Mounting is a construction operation: rail is optional, the bearing position is exact. */
  private static void installBelow(
      ServerLevel level, UseOnContext context, ServerSubLevel carrier) {
    if (context.getClickedFace() != Direction.DOWN)
      throw new IllegalArgumentException("message.guidedrailways.placement.underside");
    var pos = context.getClickedPos().below();
    if (!level.getBlockState(pos).canBeReplaced())
      throw new IllegalArgumentException("message.guidedrailways.placement.no_space");
    var pipe = SubLevelPhysicsSystem.get(level).getPipeline();
    var rotation = new Quaterniond(pipe.readPose(carrier, new Pose3d()).orientation());
    var pivot =
        world(carrier, new Vector3d(pos.getX() + .5, pos.getY() + PLATE_BOTTOM, pos.getZ() + .5));
    var selection =
        PhysicalBogeySelection.selected(
            (net.minecraft.server.level.ServerPlayer) context.getPlayer());
    var gauge = selection.gauge() == null ? RailGauge.STANDARD : selection.gauge();
    var origin =
        new Vector3d(pivot)
            .sub(new Vector3d(0, selection.variant().compactPivotHeight(), 0).rotate(rotation));
    checkPlacementSpace(level, origin, rotation, selection.variant(), List.of());
    var bogie =
        createBody(
            level,
            Content.BOGIE
                .get()
                .defaultBlockState()
                .setValue(BogieBlock.STYLE, selection.variant().ordinal()),
            origin,
            rotation);
    applyBogeySelection(bogie, selection);
    var tag = bogie.getUserDataTag();
    if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
    tag.putBoolean("GuidedDerailed", true);
    bogie.setUserDataTag(tag);
    try {
      level.setBlock(
          pos,
          Content.MOUNT
              .get()
              .defaultBlockState()
              .setValue(MountBlock.COMPACT, true)
              .setValue(BogieBlock.STYLE, selection.variant().ordinal()),
          3);
      if (!(level.getBlockEntity(pos) instanceof MountEntity mount))
        throw new IllegalStateException("message.guidedrailways.placement.mount_failed");
      mount.gauge = gauge;
      mount.variant = selection.variant();
      mount.bogieId = bogie.getUniqueId();
      mount.setChanged();
      register(mount);
      carrier.buildMassTracker();
      carrier.updateMergedMassData(1);
      pipe.onStatsChanged(carrier);
      tick(level);
      context
          .getPlayer()
          .displayClientMessage(
              net.minecraft.network.chat.Component.translatable(
                  "message.guidedrailways.placement.installed"),
              true);
    } catch (RuntimeException e) {
      SubLevelContainer.getContainer(level).removeSubLevel(bogie, SubLevelRemovalReason.REMOVED);
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      throw e;
    }
  }

  private static void applyBogeySelection(
      ServerSubLevel body, PhysicalBogeySelection.Selection selection) {
    if (body.getLevel().getBlockEntity(body.getPlot().getCenterBlock())
        instanceof BogieEntity entity) {
      entity.bogeyData = selection.data().copy();
      entity.setChanged();
      entity.sendData();
    }
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[BogeySelection] body={} variant={} gauge={}",
            body.getUniqueId(),
            selection.variant(),
            BogieBlock.gauge(body.getLevel().getBlockState(body.getPlot().getCenterBlock())));
  }

  private static void seedPlacement(
      Unit u, RailPlacement.Bogie fit, TrackNetwork.PlacementRoute route) {
    u.samples[0] = route.sample(fit.front());
    u.samples[1] = route.sample(fit.rear());
    u.derailGraceUntil = u.bogie.getLevel().getGameTime() + 40;
  }

  private static void checkPlacementSpace(
      ServerLevel level,
      Vector3dc origin,
      Quaterniond rotation,
      BogeyVariant variant,
      List<Unit> excluded) {
    var offset = BogiePairContact.offset(variant);
    var half = BogiePairContact.half(variant);
    var centre = new Vector3d(offset).rotate(rotation).add(origin);
    for (int i = 0; i < 8; i++) {
      var corner =
          new Vector3d(
                  (i & 1) == 0 ? -half.x() : half.x(),
                  (i & 2) == 0 ? -half.y() : half.y(),
                  (i & 4) == 0 ? -half.z() : half.z())
              .rotate(rotation)
              .add(centre);
      if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(corner.x, corner.y, corner.z)))
        throw new IllegalArgumentException("message.guidedrailways.placement.border");
    }
    var candidate = new BogiePairContact.Pose(centre, rotation, true, half);
    for (var u : VehiclePhysics.cargoUnits(level))
      if (!excluded.contains(u) && !u.bogie.isRemoved()) {
        var other =
            world(
                u.bogie, new Vector3d(u.localCenter).add(BogiePairContact.offset(u.mount.variant)));
        var q =
            new Quaterniond(
                SubLevelPhysicsSystem.get(level)
                    .getPipeline()
                    .readPose(u.bogie, new Pose3d())
                    .orientation());
        var contact =
            BogiePairContact.find(
                candidate,
                new BogiePairContact.Pose(other, q, true, BogiePairContact.half(u.mount.variant)),
                null);
        if (contact != null && contact.gap() < .03)
          throw new IllegalArgumentException("message.guidedrailways.placement.too_close");
      }
  }

  private static ServerSubLevel createBody(
      ServerLevel level, BlockState state, Vector3d origin, Quaterniond rotation) {
    var container = SubLevelContainer.getContainer(level);
    var body = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
    try {
      var plot = body.getPlot();
      plot.newEmptyChunk(plot.getCenterChunk());
      var pos = plot.getCenterBlock();
      var ref = center(body);
      var chunk = level.getChunkAt(pos);
      var old = chunk.getBlockState(pos);
      chunk.setBlockState(pos, state, true);
      SubLevelAssemblyHelper.markAndNotifyBlock(level, pos, chunk, old, state, 3, 512);
      if (state.is(Content.BOGIE.get()) && !CollisionAssembler.populate(body, false))
        throw new IllegalStateException("message.guidedrailways.placement.bogie_failed");
      body.buildMassTracker();
      body.updateMergedMassData(1);
      var com = body.getMassTracker().getCenterOfMass();
      body.logicalPose().rotationPoint().set(com);
      SubLevelPhysicsSystem.get(level)
          .getPipeline()
          .teleport(body, new Vector3d(com).sub(ref).rotate(rotation).add(origin), rotation);
      body.updateLastPose();
      body.updateBoundingBox();
      body.setName(
          net.minecraft.network.chat.Component.translatable(
                  state.is(Content.MOUNT.get())
                      ? "block.guidedrailways.bogie_mount"
                      : "block.guidedrailways.bogie_frame")
              .getString());
      return body;
    } catch (RuntimeException error) {
      container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
      throw error;
    }
  }

  static void unload(ServerLevel level) {
    RELOCATING.values().removeIf(r -> r.level == level);
  }
}
