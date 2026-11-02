package com.physicalrailways;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;

/** Isolated teaching scenes; no actual physics, scheduling, inventory or coupling transactions. */
final class RailwayOperationScenes {
  static void coupling(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_coupling");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    scene.world().showSection(util.select().fromTo(3, 2, 6, 5, 4, 8), Direction.DOWN);
    var second = scene.world().showIndependentSectionImmediately(util.select().position(4, 5, 7));
    double y = 1 + BogieGeometry.RAIL_TOP + BogieGeometry.TREAD_RADIUS
        - (5 + BogieGeometry.RENDER_Y + BogieGeometry.AXLE_RENDER_Y);
    scene.world().moveSection(second, new net.minecraft.world.phys.Vec3(0, y, 0), 0);
    PhysicalRailwayScenes.text(scene, "couple_install", util.vector().centerOf(4, 4, 5));
    scene.overlay().showControls(util.vector().centerOf(4, 4, 5), Pointing.RIGHT, 40).rightClick().withItem(Content.COUPLER_LINK.get().getDefaultInstance());
    scene.idle(45);
    scene.overlay().showControls(util.vector().centerOf(4, 4, 6), Pointing.RIGHT, 60).rightClick().withItem(Content.COUPLER_LINK.get().getDefaultInstance());
    PhysicalRailwayScenes.text(scene, "couple_select", util.vector().centerOf(4, 4, 6));
    scene.overlay().showOutline(PonderPalette.GREEN, "coupled_consist", util.select().fromTo(3, 2, 3, 5, 4, 8), 120);
    PhysicalRailwayScenes.text(scene, "couple_group", util.vector().centerOf(4, 4, 5));
    PhysicalRailwayScenes.text(scene, "couple_follow", util.vector().centerOf(4, 4, 5));
    scene.overlay().showControls(util.vector().centerOf(4, 4, 5), Pointing.RIGHT, 60).rightClick().withItem(Content.COUPLER_LINK.get().getDefaultInstance());
    PhysicalRailwayScenes.text(scene, "couple_unlink", util.vector().centerOf(4, 4, 5));
    PhysicalRailwayScenes.text(scene, "couple_limits", util.vector().centerOf(4, 4, 5));
    scene.markAsFinished();
  }
  static void settings(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_settings");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    scene.overlay().showControls(util.vector().topOf(4, 4, 3), Pointing.RIGHT, 60).rightClick().whileSneaking();
    PhysicalRailwayScenes.text(scene, "settings_open", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "settings_direction", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "settings_gears", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "settings_name", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "settings_goggles", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "settings_priority", util.vector().topOf(4, 4, 3));
    scene.markAsFinished();
  }
  static void schedule(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_schedule");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "schedule_station", util.vector().topOf(1, 1, 4));
    scene.overlay().showControls(util.vector().topOf(4, 4, 3), Pointing.RIGHT, 60).rightClick().withItem(AllItems.SCHEDULE.asStack());
    PhysicalRailwayScenes.text(scene, "schedule_install", util.vector().topOf(1, 1, 4));
    PhysicalRailwayScenes.text(scene, "schedule_actions", util.vector().topOf(1, 1, 4));
    PhysicalRailwayScenes.text(scene, "schedule_instructions", util.vector().topOf(1, 1, 4));
    PhysicalRailwayScenes.text(scene, "schedule_manual", util.vector().topOf(1, 1, 4));
    PhysicalRailwayScenes.text(scene, "schedule_recovery", util.vector().topOf(1, 1, 4));
    PhysicalRailwayScenes.text(scene, "schedule_boundary", util.vector().topOf(1, 1, 4));
    scene.markAsFinished();
  }
  static void conditions(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_conditions");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "wait_time", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "wait_redstone", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "wait_cargo", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "wait_idle", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "wait_passengers", util.vector().centerOf(3, 4, 3));
    PhysicalRailwayScenes.text(scene, "wait_resume", util.vector().topOf(4, 4, 3));
    scene.markAsFinished();
  }
  static void network(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_network");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "network_selector", PhysicalRailwayScenes.consolePoint(9, 12, 12));
    PhysicalRailwayScenes.text(scene, "network_commit", PhysicalRailwayScenes.consolePoint(11, 12, 12));
    PhysicalRailwayScenes.text(scene, "network_auto", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "network_signals", util.vector().centerOf(2, 1, 2));
    PhysicalRailwayScenes.text(scene, "network_observers", util.vector().centerOf(2, 1, 6));
    PhysicalRailwayScenes.text(scene, "network_display", util.vector().centerOf(1, 3, 4));
    PhysicalRailwayScenes.text(scene, "network_readonly", util.vector().topOf(4, 4, 3));
    scene.markAsFinished();
  }
  static void cargo(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_cargo");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "cargo_storage", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "cargo_path", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "cargo_scope", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "cargo_connectors", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "cargo_packages", util.vector().centerOf(5, 4, 4));
    PhysicalRailwayScenes.text(scene, "cargo_blocked", util.vector().centerOf(5, 4, 4));
    scene.markAsFinished();
  }
  static void variants(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "physical_variants");
    PhysicalRailwayScenes.tracks(scene, util);
    PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 4, 7), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "variant_select", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "variant_gauge", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "variant_multi", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "variant_power", util.vector().topOf(4, 4, 3));
    PhysicalRailwayScenes.text(scene, "variant_limits", util.vector().topOf(4, 4, 3));
    scene.markAsFinished();
  }
  private RailwayOperationScenes() {}
}
