package com.guidedrailways;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.*;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

final class PhysicalRailwayScenes {
  static final BlockPos WHEELS = new BlockPos(4, 5, 4);
  static final BlockPos MOUNT = new BlockPos(4, 2, 4);
  static final BlockPos CONSOLE = new BlockPos(4, 4, 3);

  record Vehicle(ElementLink<WorldSectionElement> wheels, ElementLink<WorldSectionElement> body) {}

  static void begin(SceneBuilder scene, String id) {
    scene.title(id, RailwayPonderPlugin.title(id));
    scene.configureBasePlate(0, 0, 9);
    scene.scaleSceneView(.65f);
    scene.showBasePlate();
    scene.idle(10);
  }

  static void text(SceneBuilder scene, String key, Vec3 target) {
    scene.overlay().showText(85).sharedText(key).pointAt(target).placeNearTarget().attachKeyFrame();
    scene.idle(95);
  }

  static void tracks(SceneBuilder scene, SceneBuildingUtil util) {
    scene.world().showSection(util.select().fromTo(4, 1, 0, 4, 1, 8), Direction.DOWN);
    scene.idle(15);
  }

  static Vehicle vehicle(SceneBuilder scene, SceneBuildingUtil util, Selection body) {
    var wheels = scene.world().showIndependentSectionImmediately(util.select().position(WHEELS));
    // Render the real custom bogey at its fractional rail contact height. The schematic stores
    // it above the carriage so both independent sections can occupy the same displayed column.
    double y =
        1
            + BogieGeometry.RAIL_TOP
            + BogieGeometry.TREAD_RADIUS
            - (WHEELS.getY() + BogieGeometry.RENDER_Y + BogieGeometry.AXLE_RENDER_Y);
    scene.world().moveSection(wheels, new Vec3(0, y, 0), 0);
    return new Vehicle(wheels, scene.world().showIndependentSection(body, Direction.DOWN));
  }

  static void move(
      SceneBuilder scene, SceneBuildingUtil util, Vehicle vehicle, double z, int ticks) {
    wheelSpeed(scene, util, 0, z * 20 / ticks);
    scene.world().moveSection(vehicle.wheels, new Vec3(0, 0, z), ticks);
    scene.world().moveSection(vehicle.body, new Vec3(0, 0, z), ticks);
    scene.idle(ticks);
    wheelSpeed(scene, util, z, 0);
  }

  private static void wheelSpeed(
      SceneBuilder scene, SceneBuildingUtil util, double distance, double speed) {
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(WHEELS),
            BogieEntity.class,
            tag -> {
              tag.putDouble("WheelDistance", distance);
              tag.putDouble("LinearSpeed", speed);
            });
  }

  static void bogey(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    begin(scene, "physical_bogey");
    tracks(scene, util);
    scene
        .overlay()
        .showControls(util.vector().topOf(4, 1, 4), Pointing.DOWN, 50)
        .rightClick()
        .withItem(Content.bogieItem());
    text(scene, "bogey_place", util.vector().topOf(4, 1, 4));
    var vehicle = vehicle(scene, util, util.select().position(MOUNT));
    text(scene, "bogey_physical", util.vector().topOf(MOUNT));
    scene
        .world()
        .showSectionAndMerge(util.select().fromTo(3, 3, 3, 5, 3, 5), Direction.DOWN, vehicle.body);
    scene
        .overlay()
        .showControls(util.vector().centerOf(3, 3, 4), Pointing.RIGHT, 45)
        .rightClick()
        .withItem(AllItems.SUPER_GLUE.asStack());
    text(scene, "bogey_build", util.vector().topOf(MOUNT));
    scene
        .world()
        .showSectionAndMerge(util.select().position(4, 4, 4), Direction.DOWN, vehicle.body);
    scene.world().setKineticSpeed(util.select().fromTo(4, 2, 4, 4, 4, 4), 64);
    scene.overlay().showOutline(PonderPalette.GREEN, "power", util.select().position(MOUNT), 85);
    text(scene, "bogey_power", util.vector().topOf(4, 3, 4));
    move(scene, util, vehicle, -2, 50);
    text(scene, "bogey_drive", util.vector().topOf(4, 2, 2));
    scene
        .overlay()
        .showControls(util.vector().topOf(4, 2, 2), Pointing.RIGHT, 50)
        .rightClick()
        .withItem(AllItems.WRENCH.asStack());
    text(scene, "bogey_rerail", util.vector().topOf(4, 2, 2));
    scene
        .overlay()
        .showControls(util.vector().topOf(4, 2, 2), Pointing.RIGHT, 50)
        .rightClick()
        .whileSneaking()
        .withItem(AllItems.WRENCH.asStack());
    text(scene, "bogey_remove", util.vector().topOf(4, 2, 2));
    scene.markAsFinished();
  }

  static void controls(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    begin(scene, "physical_controls");
    tracks(scene, util);
    var vehicle = vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().setKineticSpeed(util.select().fromTo(4, 2, 4, 4, 4, 4), 64);
    text(scene, "controls_install", util.vector().topOf(CONSOLE));
    scene.overlay().showControls(util.vector().topOf(CONSOLE), Pointing.RIGHT, 45).rightClick();
    text(scene, "controls_enter", util.vector().topOf(CONSOLE));
    scene.overlay().showControls(util.vector().topOf(CONSOLE), Pointing.RIGHT, 65).rightClick();
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(CONSOLE),
            DriverConsoleEntity.class,
            tag -> {
              tag.putInt("VisualNotch", 3);
              tag.putBoolean("VisualActive", true);
            });
    text(scene, "controls_lever", util.vector().topOf(CONSOLE));
    move(scene, util, vehicle, -1, 40);
    text(scene, "controls_steer", util.vector().topOf(4, 1, 1));
    scene.overlay().showControls(util.vector().topOf(4, 4, 2), Pointing.RIGHT, 45).leftClick();
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(CONSOLE),
            DriverConsoleEntity.class,
            tag -> {
              tag.putInt("VisualNotch", -4);
              tag.putBoolean("VisualEmergency", true);
            });
    text(scene, "controls_brake", util.vector().topOf(4, 4, 2));
    scene.overlay().showControls(util.vector().topOf(4, 4, 2), Pointing.RIGHT, 45).rightClick();
    text(scene, "controls_reset", util.vector().topOf(4, 4, 2));
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(CONSOLE),
            DriverConsoleEntity.class,
            tag -> tag.putBoolean("VisualEmergency", false));
    scene
        .overlay()
        .showControls(util.vector().topOf(4, 4, 2), Pointing.RIGHT, 45)
        .rightClick()
        .whileSneaking();
    text(scene, "controls_settings", util.vector().topOf(4, 4, 2));
    scene
        .overlay()
        .showControls(util.vector().topOf(4, 4, 2), Pointing.RIGHT, 45)
        .rightClick()
        .withItem(AllItems.SCHEDULE.asStack());
    text(scene, "controls_schedule", util.vector().topOf(4, 4, 2));
    text(scene, "controls_observe", util.vector().topOf(4, 4, 2));
    scene.markAsFinished();
  }

  private PhysicalRailwayScenes() {}
}
