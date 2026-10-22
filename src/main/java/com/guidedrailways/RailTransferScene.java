package com.guidedrailways;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Referenced only when Simulated is installed. Never creates real docking locks or constraints. */
final class RailTransferScene {
  private static final BlockPos WORLD = new BlockPos(1, 4, 4);
  private static final BlockPos TRAIN = new BlockPos(3, 4, 4);
  private static final BlockPos STATION = new BlockPos(1, 1, 6);
  private static final BlockPos MAILBOX = new BlockPos(1, 1, 7);

  static void transfer(SceneBuilder builder, SceneBuildingUtil util) {
    var scene = new CreateSceneBuilder(builder);
    PhysicalRailwayScenes.begin(scene, "rail_transfer");
    PhysicalRailwayScenes.tracks(scene, util);
    var vehicle =
        PhysicalRailwayScenes.vehicle(scene, util, util.select().fromTo(3, 2, 3, 5, 4, 5));
    scene.world().showSection(util.select().fromTo(0, 1, 4, 1, 4, 4), Direction.DOWN);
    scene
        .world()
        .showSection(
            util.select().position(STATION).add(util.select().position(MAILBOX)), Direction.DOWN);
    PhysicalRailwayScenes.text(scene, "transfer_pair", util.vector().centerOf(TRAIN));
    scene
        .overlay()
        .showControls(util.vector().centerOf(WORLD), Pointing.RIGHT, 45)
        .rightClick()
        .withItem(AllItems.WRENCH.asStack());
    PhysicalRailwayScenes.text(scene, "transfer_select", util.vector().centerOf(WORLD));
    scene
        .overlay()
        .showControls(util.vector().centerOf(STATION), Pointing.RIGHT, 45)
        .rightClick()
        .withItem(AllItems.WRENCH.asStack());
    scene
        .overlay()
        .showLine(
            PonderPalette.BLUE, util.vector().centerOf(WORLD), util.vector().centerOf(STATION), 85);
    PhysicalRailwayScenes.text(scene, "transfer_bind", util.vector().centerOf(STATION));
    scene
        .overlay()
        .showLine(
            PonderPalette.GREEN,
            util.vector().centerOf(MAILBOX),
            util.vector().centerOf(STATION),
            85);
    PhysicalRailwayScenes.text(scene, "transfer_address", util.vector().centerOf(MAILBOX));
    PhysicalRailwayScenes.text(scene, "transfer_stop", new Vec3(2.5, 4.5, 4.5));
    extend(scene, WORLD, true);
    extend(scene, TRAIN, true);
    scene.idle(25);
    scene
        .overlay()
        .showOutline(
            PonderPalette.GREEN,
            "docks",
            util.select().position(WORLD).add(util.select().position(TRAIN)),
            85);
    PhysicalRailwayScenes.text(scene, "transfer_lock", new Vec3(2.5, 4.5, 4.5));
    scene
        .overlay()
        .showControls(new Vec3(2.5, 4.5, 4.5), Pointing.DOWN, 60)
        .withItem(PackageStyles.getDefaultBox());
    scene
        .overlay()
        .showBigLine(PonderPalette.GREEN, new Vec3(3, 4.5, 4.5), new Vec3(2, 4.5, 4.5), 85);
    PhysicalRailwayScenes.text(scene, "transfer_cargo", util.vector().centerOf(4, 4, 4));
    scene.overlay().showControls(util.vector().centerOf(WORLD), Pointing.RIGHT, 45).rightClick();
    PhysicalRailwayScenes.text(scene, "transfer_inspect", util.vector().centerOf(WORLD));
    extend(scene, WORLD, false);
    extend(scene, TRAIN, false);
    scene.idle(25);
    PhysicalRailwayScenes.text(scene, "transfer_release", util.vector().centerOf(TRAIN));
    PhysicalRailwayScenes.move(scene, util, vehicle, -2, 50);
    scene.markAsFinished();
  }

  private static void extend(SceneBuilder scene, BlockPos pos, boolean extended) {
    scene
        .world()
        .modifyBlock(pos, state -> state.setValue(BlockStateProperties.POWERED, extended), false);
    scene
        .world()
        .modifyBlockEntity(
            pos,
            DockingConnectorBlockEntity.class,
            dock -> {
              dock.powered = extended;
              dock.setVirtualLock(extended);
              dock.extension.chase(extended ? 1 : 0, .1, LerpedFloat.Chaser.LINEAR);
              dock.feet.chase(extended ? 1 : 0, .1, LerpedFloat.Chaser.LINEAR);
            });
  }

  private RailTransferScene() {}
}
