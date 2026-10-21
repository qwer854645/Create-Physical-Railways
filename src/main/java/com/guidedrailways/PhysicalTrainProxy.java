package com.guidedrailways;

import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Query-only native identity. All physical motion and scheduling remain in the existing backend.
 */
public final class PhysicalTrainProxy extends Train {
  private ResourceKey<Level> dimension = Level.OVERWORLD;
  private List<String> bodyIds = List.of();
  private int length;
  private CompoundTag snapshot = new CompoundTag();
  final NativeTrainJourney journey = new NativeTrainJourney();

  PhysicalTrainProxy(UUID id) {
    super(
        id,
        new UUID(0, 0),
        null,
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        Component.translatable("gui.guidedrailways.identity.name", id.toString().substring(0, 8)),
        TrainIconType.getDefault(),
        0);
    navigation = new PhysicalNavigation(this);
    runtime = new PhysicalScheduleRuntime(this);
  }

  public CompoundTag snapshot() {
    return snapshot.copy();
  }

  void apply(
      CompoundTag data,
      Map<UUID, TrackGraph> graphs,
      net.minecraft.core.HolderLookup.Provider registries) {
    snapshot = data.copy();
    String customName = TrainName.normalize(data.getString("Name"));
    name =
        customName.isEmpty()
            ? Component.translatable(
                "gui.guidedrailways.identity.name", id.toString().substring(0, 8))
            : UserText.userInput(customName);
    dimension =
        ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.parse(data.getString("Dimension")));
    graph = data.hasUUID("Graph") ? graphs.get(data.getUUID("Graph")) : null;
    speed = data.getDouble("Speed") / 20; // Create uses blocks/tick; backend reports blocks/second.
    targetSpeed = speed;
    currentlyBackwards = speed < 0;
    derailed = data.getBoolean("Derailed") || graph == null;
    length = data.getInt("Length");
    currentStation = data.hasUUID("Station") ? data.getUUID("Station") : null;
    var parts = data.getList("Carriages", Tag.TAG_COMPOUND);
    var ids = new ArrayList<String>();
    for (var value : parts) {
      var part = (CompoundTag) value;
      ids.add(
          part.getUUID("Body")
              + ":"
              + part.getCompound("Front").getString("Support")
              + ":"
              + part.getCompound("Front").getInt("Variant")
              + ":"
              + part.getCompound("Rear").getString("Support")
              + ":"
              + part.getCompound("Rear").getInt("Variant"));
    }
    if (!bodyIds.equals(ids)) {
      bodyIds = List.copyOf(ids);
      carriages.clear();
      carriageSpacing.clear();
      for (int i = 0; i < parts.size(); i++) {
        var tag = parts.getCompound(i);
        var carriage =
            new LogicalCarriage(
                tag.getCompound("Front"), tag.contains("Rear") ? tag.getCompound("Rear") : null);
        carriage.setTrain(this);
        carriages.add(carriage);
        if (i > 0) carriageSpacing.add(0);
      }
    }
    for (int i = 0; i < carriages.size(); i++) {
      var tag = parts.getCompound(i);
      var carriage = (LogicalCarriage) carriages.get(i);
      carriage.update(tag, dimension, graph);
      if (i > 0) {
        var previous = parts.getCompound(i - 1);
        double gap = vector(previous, "Back").distanceTo(vector(tag, "Ahead"));
        carriageSpacing.set(i - 1, (int) Math.ceil(gap));
      }
    }
    invalid = false;
    var journeyData = data.getCompound("Journey");
    ((PhysicalNavigation) navigation).project(journeyData.getCompound("Navigation"));
    ((PhysicalScheduleRuntime) runtime).project(journeyData, registries);
  }

  /** Actual station association only; never invokes the native runtime or mail transfer. */
  void station(UUID station) {
    currentStation = station;
    if (station == null) snapshot.remove("Station");
    else snapshot.putUUID("Station", station);
  }

  void clearJourney() {
    journey.reset();
    snapshot.remove("Journey");
    ((PhysicalNavigation) navigation).project(new CompoundTag());
    ((PhysicalScheduleRuntime) runtime).project(new CompoundTag(), null);
  }

  static void syncStation(
      PhysicalTrainProxy train, com.simibubi.create.content.trains.station.GlobalStation previous) {
    var current = train.getCurrentStation();
    if (previous != null && previous != current) previous.cancelReservation(train);
    if (current != null
        && !current.assembling
        && (current.getNearestTrain() == null || current.getNearestTrain() == train))
      current.reserveFor(train);
  }

  static Vec3 vector(CompoundTag tag, String key) {
    var v = tag.getCompound(key);
    return new Vec3(v.getDouble("X"), v.getDouble("Y"), v.getDouble("Z"));
  }

  private static final class LogicalBogey extends CarriageBogey {
    Vec3 anchor = Vec3.ZERO;
    ResourceKey<Level> dimension = Level.OVERWORLD;

    LogicalBogey(CompoundTag tag) {
      super(block(tag), false, bogeyData(tag), new TravellingPoint(), new TravellingPoint());
    }

    private static CompoundTag bogeyData(CompoundTag tag) {
      var data = new CompoundTag();
      var variant = BogeyVariant.read(tag.getInt("Variant"));
      data.putString(
          "BogeyStyle", variant == BogeyVariant.DEFAULT ? "create:standard" : variant.id);
      return data;
    }

    private static AbstractBogeyBlock<?> block(CompoundTag tag) {
      var variant = BogeyVariant.read(tag.getInt("Variant"));
      if (variant == BogeyVariant.DEFAULT) return com.simibubi.create.AllBlocks.SMALL_BOGEY.get();
      var style =
          com.simibubi.create.AllBogeyStyles.BOGEY_STYLES.get(
              ResourceLocation.tryParse(variant.id));
      var size = BogeySizes.all().get(ResourceLocation.tryParse(variant.size));
      return style != null && size != null && style.validSizes().contains(size)
          ? style.getBlockForSize(size)
          : com.simibubi.create.AllBlocks.SMALL_BOGEY.get();
    }

    void update(CompoundTag tag, ResourceKey<Level> dim, TrackGraph graph) {
      dimension = dim;
      anchor = vector(tag, "Center");
      updatePoint(leading(), tag.getCompound("P0"), graph);
      updatePoint(trailing(), tag.getCompound("P1"), graph);
      couplingAnchors = Couple.create(anchor, anchor);
    }

    @Override
    public Vec3 getAnchorPosition() {
      return anchor;
    }

    @Override
    public Vec3 getAnchorPosition(boolean leading) {
      return anchor;
    }

    @Override
    public ResourceKey<Level> getDimension() {
      return dimension;
    }

    @Override
    public double getStress() {
      return 0;
    }
  }

  static void updatePoint(TravellingPoint point, CompoundTag tag, TrackGraph graph) {
    point.node1 = graph == null || !tag.contains("From") ? null : graph.getNode(tag.getInt("From"));
    point.node2 = graph == null || !tag.contains("To") ? null : graph.getNode(tag.getInt("To"));
    point.edge =
        point.node1 == null || point.node2 == null
            ? null
            : TrackNetwork.connections(graph, point.node1).get(point.node2);
    point.position =
        point.edge == null ? 0 : Math.clamp(tag.getDouble("Offset"), 0, point.edge.getLength());
    point.blocked = point.edge == null;
  }

  private static final class LogicalCarriage extends Carriage {
    LogicalCarriage(CompoundTag front, CompoundTag rear) {
      super(new LogicalBogey(front), rear == null ? null : new LogicalBogey(rear), 0);
    }

    void update(CompoundTag tag, ResourceKey<Level> dimension, TrackGraph graph) {
      ((LogicalBogey) bogeys.getFirst()).update(tag.getCompound("Front"), dimension, graph);
      if (bogeys.getSecond() instanceof LogicalBogey rear)
        rear.update(tag.getCompound("Rear"), dimension, graph);
      var ahead = vector(tag, "Ahead");
      var back = vector(tag, "Back");
      bogeySpacing =
          bogeys.getSecond() instanceof LogicalBogey rear
              ? (int) Math.round(((LogicalBogey) bogeys.getFirst()).anchor.distanceTo(rear.anchor))
              : 0;
      var dimensional = getDimensional(dimension);
      dimensional.positionAnchor = ahead.add(back).scale(.5);
      dimensional.rotationAnchors = Couple.create(ahead, back);
      dimensional.pointsInitialised = true;
    }

    @Override
    public void manageEntities(Level level) {}

    @Override
    public void setContraption(Level level, CarriageContraption contraption) {}

    @Override
    public double travel(
        Level level,
        TrackGraph graph,
        double distance,
        TravellingPoint front,
        TravellingPoint back,
        int type) {
      return 0;
    }

    @Override
    public boolean isOnIncompatibleTrack() {
      return train.derailed;
    }
  }

  @Override
  public void earlyTick(Level level) {}

  @Override
  public void tick(Level level) {}

  @Override
  public void crash() {}

  @Override
  public void burnFuel() {}

  @Override
  public void approachTargetSpeed(float modifier) {}

  @Override
  public void collectInitiallyOccupiedSignalBlocks() {}

  @Override
  public void detachFromTracks() {}

  @Override
  public void reattachToTracks(Level level) {}

  @Override
  public boolean canDisassemble() {
    return false;
  }

  @Override
  public boolean disassemble(net.minecraft.core.Direction direction, BlockPos pos) {
    return false;
  }

  @Override
  public void arriveAt(GlobalStation station) {}

  @Override
  public void setCurrentStation(GlobalStation station) {}

  @Override
  public void leaveStation() {}

  @Override
  public int getTotalLength() {
    return length;
  }

  @Override
  public List<ResourceKey<Level>> getPresentDimensions() {
    return List.of(dimension);
  }
}
