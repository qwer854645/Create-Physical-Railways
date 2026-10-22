package com.guidedrailways;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;

/**
 * Uses CEE's own wire and pantograph visuals, without running an electrical or physics simulation.
 */
final class ElectricTrainScene {
  private static final BlockPos FRONT = new BlockPos(4, 6, 1), BACK = new BlockPos(4, 6, 7);
  private static final BlockPos SUPPLY = new BlockPos(1, 1, 1), GROUND = new BlockPos(0, 1, 1);
  private static final BlockPos PANTOGRAPH = new BlockPos(4, 4, 4);

  static void electric(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    var wires = new WireConnectionInstructions(builder);
    PhysicalRailwayScenes.begin(scene, "electric_controls");
    PhysicalRailwayScenes.tracks(scene, util);
    var vehicle =
        PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    PhysicalRailwayScenes.text(scene, "electric_install", util.vector().topOf(PANTOGRAPH));
    scene
        .world()
        .showSection(
            util.select().fromTo(2, 1, 1, 4, 6, 1).add(util.select().fromTo(2, 1, 7, 4, 6, 7)),
            Direction.DOWN);
    scene
        .overlay()
        .showControls(util.vector().centerOf(FRONT), Pointing.RIGHT, 35)
        .rightClick()
        .withItem(CEEItems.COPPER_WIRE_SPOOL.asStack());
    scene.idle(40);
    scene
        .overlay()
        .showControls(util.vector().centerOf(BACK), Pointing.RIGHT, 35)
        .rightClick()
        .withItem(CEEItems.COPPER_WIRE_SPOOL.asStack());
    wires.createCatenaryConnection(FRONT, BACK, CEEWireTypes.STANDARD.get(), 20);
    PhysicalRailwayScenes.text(scene, "electric_wire", util.vector().topOf(4, 5, 4));
    scene.world().showSection(util.select().fromTo(0, 1, 1, 1, 1, 1), Direction.DOWN);
    wires.createConnection(new InWorldNode(1, SUPPLY), new InWorldNode(0, FRONT));
    wires.createConnection(new InWorldNode(0, SUPPLY), new InWorldNode(0, GROUND));
    PhysicalRailwayScenes.text(scene, "electric_supply", util.vector().topOf(SUPPLY));
    scene.overlay().showControls(util.vector().topOf(PANTOGRAPH), Pointing.RIGHT, 40).rightClick();
    pantographs(scene, util, true);
    scene.idle(30);
    scene
        .overlay()
        .showOutline(
            PonderPalette.GREEN, "pantographs", util.select().fromTo(4, 4, 4, 4, 4, 5), 85);
    PhysicalRailwayScenes.text(scene, "electric_contact", util.vector().topOf(PANTOGRAPH));
    scene
        .overlay()
        .showControls(util.vector().topOf(PhysicalRailwayScenes.CONSOLE), Pointing.RIGHT, 40)
        .rightClick();
    PhysicalRailwayScenes.text(
        scene, "electric_enter", util.vector().topOf(PhysicalRailwayScenes.CONSOLE));
    scene
        .overlay()
        .showControls(util.vector().topOf(PhysicalRailwayScenes.CONSOLE), Pointing.RIGHT, 65)
        .rightClick();
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(PhysicalRailwayScenes.CONSOLE),
            DriverConsoleEntity.class,
            tag -> {
              tag.putInt("VisualNotch", 3);
              tag.putBoolean("VisualActive", true);
            });
    PhysicalRailwayScenes.text(
        scene, "electric_drive", util.vector().topOf(PhysicalRailwayScenes.CONSOLE));
    PhysicalRailwayScenes.move(scene, util, vehicle, -1, 40);
    PhysicalRailwayScenes.text(scene, "electric_limit", util.vector().topOf(4, 4, 3));
    pantographs(scene, util, false);
    scene.idle(30);
    PhysicalRailwayScenes.text(scene, "electric_loss", util.vector().topOf(4, 4, 3));
    scene.overlay().showControls(util.vector().topOf(4, 4, 2), Pointing.RIGHT, 40).leftClick();
    scene
        .world()
        .modifyBlockEntityNBT(
            util.select().position(PhysicalRailwayScenes.CONSOLE),
            DriverConsoleEntity.class,
            tag -> {
              tag.putInt("VisualNotch", -4);
              tag.putBoolean("VisualEmergency", true);
            });
    PhysicalRailwayScenes.text(scene, "electric_brake", util.vector().topOf(4, 4, 2));
    scene.markAsFinished();
  }

  private static void pantographs(SceneBuilder scene, SceneBuildingUtil util, boolean extended) {
    for (int z : new int[] {4, 5})
      scene
          .world()
          .modifyBlockEntity(
              new BlockPos(4, 4, z),
              PantographBlockEntity.class,
              p -> {
                p.extended = extended;
                p.targetExtensionState = extended ? 1.7f : 0;
              });
  }

  private ElectricTrainScene() {}
}
