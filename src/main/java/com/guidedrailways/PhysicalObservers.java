package com.guidedrailways;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.observer.TrackObserver;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Only covered edges are visited; native pulse, identity and redstone logic stay in Create. */
final class PhysicalObservers {
  /** One context per consist per tick, shared by axle sweeps, carriage spans and overhangs. */
  static final class Context {
    private final ServerLevel level;
    private final TrainConsists.Group group;
    private boolean resolved;
    private PhysicalTrainProxy train;
    private List<Integer> versions;
    private boolean cargoReady;

    Context(ServerLevel level, TrainConsists.Group group) {
      this.level = level;
      this.group = group;
    }

    void cover(TrackNetwork.Path path, double low, double high) {
      var span = ObserverSpan.between(low, high, path.edge().getLength());
      if (span == null) return;
      for (var point : path.edge().getEdgeData().getPoints())
        if (point instanceof TrackObserver observer
            && span.contains(observer.getLocationOn(path.edge()))) {
          if (!resolved) {
            train = NativeTrainBridge.registered(level, group);
            resolved = true;
          }
          var filter = observer.getFilter();
          if (!filter.isEmpty() && !matches(filter)) continue;
          if (!observer.isActivated())
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .info(
                    "[PhysicalObserver] observer={} graph={} edge={}/{} train={}",
                    observer.id,
                    path.graph(),
                    path.a(),
                    path.b(),
                    train == null ? null : train.id);
          if (train != null) observer.keepAlive(train);
          else if (observer.getCurrentTrain() == null)
            ((ObserverActivation) observer).guidedrailways$keepAlive();
        }
    }

    private boolean matches(FilterItemStack filter) {
      // Uncontrolled structures retain basic detection without manufacturing train identities.
      if (train == null) return false;
      if (versions == null) {
        cargoReady = train.cargoReady();
        versions = train.carriages.stream().map(c -> c.storage.getVersion()).toList();
      }
      return train.observerFilters.test(filter, versions, cargoReady, () -> query(filter));
    }

    private boolean query(FilterItemStack filter) {
      for (var carriage : train.carriages) {
        var items = carriage.storage.getAllItems();
        for (int i = 0; i < items.getSlots(); i++) {
          var stack = items.getStackInSlot(i);
          if (!stack.isEmpty() && filter.test(level, stack)) return true;
        }
        var fluids = carriage.storage.getFluids();
        for (int i = 0; i < fluids.getTanks(); i++) {
          var stack = fluids.getFluidInTank(i);
          if (!stack.isEmpty() && filter.test(level, stack)) return true;
        }
      }
      return false;
    }
  }

  private PhysicalObservers() {}
}
