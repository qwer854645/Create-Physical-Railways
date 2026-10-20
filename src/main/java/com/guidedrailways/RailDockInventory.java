package com.guidedrailways;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorSoloInventory;

/** Identifies the owner without restricting local staging, saving or recovery of this buffer. */
final class RailDockInventory extends DockingConnectorSoloInventory {
  final RailDockEntity owner;

  RailDockInventory(RailDockEntity owner) {
    this.owner = owner;
  }
}
