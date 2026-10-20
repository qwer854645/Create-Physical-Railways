package com.guidedrailways;

import com.simibubi.create.content.trains.observer.TrackObserver;

/**
 * Only observers on covered track edges are visited; native pulse/redstone logic stays in Create.
 */
final class PhysicalObservers {
  private PhysicalObservers() {}

  static void cover(TrackNetwork.Path path, double low, double high) {
    var span = ObserverSpan.between(low, high, path.edge().getLength());
    if (span == null) return;
    for (var point : path.edge().getEdgeData().getPoints())
      if (point instanceof TrackObserver observer
          && observer.getFilter().isEmpty()
          && span.contains(observer.getLocationOn(path.edge()))) {
        if (!observer.isActivated())
          org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
              .info(
                  "[PhysicalObserver] observer={} graph={} edge={}/{} position={}",
                  observer.id,
                  path.graph(),
                  path.a(),
                  path.b(),
                  observer.getLocationOn(path.edge()));
        ((ObserverActivation) observer).guidedrailways$keepAlive();
      }
  }
}
