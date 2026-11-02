package com.guidedrailways;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;

/** Only actual Sable occupants migrate. Bystanders are never selected by distance. */
final class TransferPassengers {
  static final int MAX_ENTITIES = 128;
  private record Rider(UUID id, UUID body, UUID vehicle, boolean plot, Entity original,
      Vec3 before, Vec3 after, float yaw, float pitch, CompoundTag data) {}
  private final ServerLevel source, target;
  private final PortalTransform transform;
  private final List<Rider> riders = new ArrayList<>();
  private boolean detached;

  TransferPassengers(ServerLevel source, ServerLevel target, Map<UUID, ServerSubLevel> bodies,
      Map<UUID, TransferPlots.Move> moves, PortalTransform transform, Set<net.minecraft.world.level.ChunkPos> chunks) {
    this.source = source; this.target = target; this.transform = transform;
    var candidates = new LinkedHashMap<UUID, Entity>();
    for (var body : bodies.values()) {
      var internal = body.getPlot().getBoundingBox();
      var box = new AABB(internal.minX(), internal.minY(), internal.minZ(), internal.maxX() + 1, internal.maxY() + 1, internal.maxZ() + 1);
      var nearby = new HashSet<>(source.getEntities(null, body.boundingBox().toMojang().inflate(2)));
      nearby.addAll(source.getEntities(null, box));
      for (var entity : nearby) {
        if (entity instanceof SuperGlueEntity || owner(source, entity, bodies) == null) continue;
        entity.getRootVehicle().getSelfAndPassengers().limit(MAX_ENTITIES + 1L).forEach(member -> candidates.put(member.getUUID(), member));
        if (candidates.size() > MAX_ENTITIES) throw error("limit");
      }
    }
    for (var entity : candidates.values()) {
      var body = owner(source, entity, bodies);
      if (body == null || entity.isRemoved() || !entity.canChangeDimensions(source, target)
          || entity instanceof Mob mob && mob.isLeashed()) throw error("entities");
      // The direct vanilla teleport API has no travel event. Respect dimension restrictions in preflight.
      if (!net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(entity, target.dimension())
          || entity.level() != source || entity.isRemoved()) throw error("entities");
      if (target.getEntity(entity.getUUID()) != null) throw error("occupied");
      boolean plot = SableCompanion.INSTANCE.getContaining(source, entity.position()) == body;
      if (plot && entity instanceof ServerPlayer) throw error("entities");
      var after = plot ? moves.get(body.getUniqueId()).point(entity.position()) : transform.point(entity.position());
      var data = new CompoundTag();
      if (!(entity instanceof ServerPlayer) && !entity.saveAsPassenger(data)) throw error("entities");
      data.remove("Passengers"); // Each UUID is captured once; riding edges are stored separately.
      if (!plot) {
        var arrival = transform.bounds(entity.getBoundingBox()).deflate(.001);
        PhysicalTransfer.loadArea(target, arrival, chunks);
        if (arrival.minY < target.getMinBuildHeight() || arrival.maxY > target.getMaxBuildHeight()
            || !target.getWorldBorder().isWithinBounds(arrival) || !target.noCollision(arrival)
            || !target.getEntities(entity, arrival).isEmpty()
            || SubLevelContainer.getContainer(target).getAllSubLevels().stream()
                .anyMatch(other -> !other.isRemoved() && other.boundingBox().toMojang().intersects(arrival))) throw error("blocked");
      }
      riders.add(new Rider(entity.getUUID(), body.getUniqueId(), entity.getVehicle() == null ? null : entity.getVehicle().getUUID(),
          plot, entity, entity.position(), after, entity.getYRot(), entity.getXRot(), data));
    }
    var ids = new HashSet<UUID>(); riders.forEach(rider -> ids.add(rider.id));
    if (riders.stream().anyMatch(rider -> rider.vehicle != null && !ids.contains(rider.vehicle))) throw error("entities");
  }

  private static ServerSubLevel owner(ServerLevel level, Entity entity, Map<UUID, ServerSubLevel> bodies) {
    var tracked = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
    if (tracked instanceof ServerSubLevel body && bodies.get(body.getUniqueId()) == body) return body;
    var plot = SableCompanion.INSTANCE.getContaining(level, entity.position());
    return plot instanceof ServerSubLevel body && bodies.get(body.getUniqueId()) == body ? body : null;
  }

  boolean owns(Entity entity) { return riders.stream().anyMatch(rider -> rider.id.equals(entity.getUUID())); }

  void detach() {
    detached = true;
    release();
  }

  void release() {
    if (!detached) return;
    for (var rider : riders) for (var level : List.of(source, target)) {
      var entity = level.getEntity(rider.id);
      if (entity != null) {
        entity.stopRiding(); clear(entity);
        // REMOVED kicks or destroys every entity still indexed in plot chunks, including seats.
        // Park internal entities in world coordinates for this synchronous transaction first.
        var body = SubLevelContainer.getContainer(level).getSubLevel(rider.body);
        if (rider.plot && body != null && !body.isRemoved()
            && SableCompanion.INSTANCE.getContaining(level, entity.position()) == body)
          entity.moveTo(body.logicalPose().transformPosition(entity.position()));
      }
    }
  }

  void move(boolean forward) {
    if (!detached) return;
    var from = forward ? source : target; var to = forward ? target : source;
    var container = SubLevelContainer.getContainer(to);
    for (var rider : riders) {
      var body = container.getSubLevel(rider.body);
      if (!(body instanceof ServerSubLevel) || body.isRemoved()) throw error("recovery");
      var entity = from.getEntity(rider.id);
      var existing = to.getEntity(rider.id);
      if (entity != null && existing != null) throw error("recovery");
      if (entity == null) entity = existing;
      if ((entity == null || entity.isRemoved())
          && rider.original instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity
          && rider.original.isRemoved()) {
        var data = rider.data.copy();
        var pos = new ListTag(); var destination = forward ? rider.after : rider.before;
        pos.add(DoubleTag.valueOf(destination.x)); pos.add(DoubleTag.valueOf(destination.y)); pos.add(DoubleTag.valueOf(destination.z)); data.put("Pos", pos);
        entity = EntityType.loadEntityRecursive(data, to, java.util.function.Function.identity());
        if (entity == null || !to.addFreshEntity(entity)) throw error("recovery");
      }
      if (entity == null || entity.isRemoved()) throw error("recovery");
      entity.stopRiding(); clear(entity);
      var position = forward ? rider.after : rider.before;
      float yaw = rider.yaw - (forward && !rider.plot ? transform.turns() * 90 : 0);
      if (!entity.teleportTo(to, position.x, position.y, position.z, Set.of(), yaw, rider.pitch)) throw error("failed");
      entity = to.getEntity(rider.id);
      if (entity == null || entity.isRemoved()) throw error("recovery");
      attach(entity, body, rider.plot, position);
    }
    for (var rider : riders) if (rider.vehicle != null) {
      var entity = to.getEntity(rider.id); var vehicle = to.getEntity(rider.vehicle);
      if (entity == null || vehicle == null || !entity.startRiding(vehicle, true)) throw error("failed");
    }
  }

  static void clear(Entity entity) {
    var movement = (EntityMovementExtension) entity;
    movement.sable$setTrackingSubLevel(null); movement.sable$setLastTrackingSubLevelID(null);
  }

  static void attach(Entity entity, dev.ryanhcode.sable.sublevel.SubLevel body, boolean plot, Vec3 position) {
    clear(entity); entity.setDeltaMovement(Vec3.ZERO); entity.fallDistance = 0; entity.setPortalCooldown();
    if (plot) ((EntityStickExtension) entity).sable$setPlotPosition(position);
    else {
      ((EntityMovementExtension) entity).sable$setTrackingSubLevel(body);
      ((EntityStickExtension) entity).sable$setPlotPosition(body.logicalPose().transformPositionInverse(position));
    }
  }

  List<CompoundTag> snapshot() {
    return riders.stream().map(rider -> {
      var tag = new CompoundTag(); tag.putUUID("Id", rider.id); tag.putUUID("Body", rider.body);
      if (rider.vehicle != null) tag.putUUID("Vehicle", rider.vehicle);
      tag.putBoolean("Player", rider.original instanceof ServerPlayer); tag.putBoolean("Plot", rider.plot);
      tag.put("Data", rider.data.copy());
      tag.put("Before", vector(rider.before)); tag.put("After", vector(rider.after));
      tag.putFloat("Yaw", rider.yaw); tag.putFloat("Pitch", rider.pitch);
      tag.putInt("Turns", transform.turns()); return tag;
    }).toList();
  }

  private static ListTag vector(Vec3 position) {
    var tag = new ListTag(); tag.add(DoubleTag.valueOf(position.x)); tag.add(DoubleTag.valueOf(position.y)); tag.add(DoubleTag.valueOf(position.z)); return tag;
  }
  private static IllegalArgumentException error(String key) { return new IllegalArgumentException("message.guidedrailways.transfer." + key); }
}
