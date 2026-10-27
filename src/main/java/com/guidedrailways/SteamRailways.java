package com.guidedrailways;

import com.railwayteam.railways.mixin_interfaces.ISwitchDisabledEdge;
import com.railwayteam.railways.util.MixinVariables;
import com.simibubi.create.content.trains.graph.TrackEdge;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Optional release-jar adapter; never constructs native trains or carriages. */
final class SteamRailways implements RailwaysCompat.Bridge {
  private final Map<ServerLevel, SwitchReservations> reservations = new WeakHashMap<>();

  static void register() {
    RailwaysCompat.bridge = new SteamRailways();
    RailwayHardware.bridge = new SteamRailwaysHardware();
    SteamRailwaysBogeys.register();
  }

  @Override
  public boolean geometry(TrackEdge from, TrackEdge to) {
    boolean previous = MixinVariables.temporarilySkipSwitches;
    try {
      MixinVariables.temporarilySkipSwitches = true;
      return from.canTravelTo(to);
    } finally {
      MixinVariables.temporarilySkipSwitches = previous;
    }
  }

  private static ISwitchDisabledEdge flags(TrackEdge edge) {
    return (ISwitchDisabledEdge) edge.getEdgeData();
  }

  @Override
  public boolean navigable(TrackEdge to) {
    var f = flags(to);
    return f.isEnabled() || f.isAutomatic();
  }

  /**
   * Reports a refused passage once a second per junction.
   *
   * <p>A schedule that stops at a junction and never moves on is either a switched-out edge or another
   * consist holding the passage; without this line both look identical in the log.
   */
  private static void diagnose(
      ServerLevel level,
      TrackNetwork.Path path,
      int node,
      int from,
      int to,
      UUID train,
      String reason) {
    long now = level.getGameTime();
    var key = level.dimension().location() + "@" + node;
    var last = DIAGNOSED.getOrDefault(key, Long.MIN_VALUE);
    if (now - last < 20) return;
    DIAGNOSED.put(key, now);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[SwitchBlocked] reason={} node={} from={} to={} train={} graph={}",
            reason,
            node,
            from,
            to,
            train,
            path.graph());
  }

  private static final java.util.Map<String, Long> DIAGNOSED = new java.util.HashMap<>();

  @Override
  public boolean pass(
      ServerLevel level,
      TrackNetwork.Path path,
      int node,
      int from,
      int to,
      UUID train,
      boolean committed) {
    var target =
        TrackNetwork.connections(path.network(), path.network().getNode(node))
            .get(path.network().getNode(to));
    if (target == null) return false;
    var f = flags(target);
    var claims = reservations.computeIfAbsent(level, l -> new SwitchReservations());
    var key = new SwitchReservations.Key(path.graph(), node);
    long now = level.getGameTime();
    if (claims.owns(key, train, from, to, now) && committed) return true;
    if (!f.isEnabled() && !f.isAutomatic()) {
      diagnose(level, path, node, from, to, train, "switch_disabled");
      return false;
    }
    if (!claims.claim(key, train, from, to, now)) {
      diagnose(level, path, node, from, to, train, "held_by_another_consist");
      return false;
    }
    if (!f.isEnabled()) {
      f.setAutomaticallySelected();
      return false;
    }
    return true;
  }

  @Override
  public void hold(ServerLevel level, UUID graph, int node, int from, int to, UUID train) {
    reservations
        .computeIfAbsent(level, l -> new SwitchReservations())
        .claim(new SwitchReservations.Key(graph, node), train, from, to, level.getGameTime());
  }

  @Override
  public void unload(ServerLevel level) {
    reservations.remove(level);
  }

  @Override
  public double[] buffers(TrackNetwork.Path path) {
    return path.edge().getEdgeData().getPoints().stream()
        .filter(p -> p instanceof com.railwayteam.railways.content.buffer.TrackBuffer)
        .mapToDouble(p -> p.getLocationOn(path.edge()))
        .toArray();
  }

  @Override
  public boolean stationEnabled(com.simibubi.create.content.trains.station.GlobalStation station) {
    return ((com.railwayteam.railways.mixin_interfaces.ILimitedGlobalStation) station)
        .isStationEnabled();
  }
}
