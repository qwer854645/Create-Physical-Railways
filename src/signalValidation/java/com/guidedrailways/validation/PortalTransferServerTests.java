package com.guidedrailways.validation;

import com.guidedrailways.PhysicalTransfer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * The transfer entry gates, on a real server with all dimensions present.
 *
 * <p>Cross-dimension transfer of a whole consist cannot be driven from a GameTest without assembling a train and a
 * portal pair, but its refusal paths can: a request that must never be accepted is exactly where a partial transfer
 * would begin. These run on a real server so the containers, physics systems and dimension registries are the real
 * ones, not fakes.
 */
@GameTestHolder("guidedrailways_signal_validation")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class PortalTransferServerTests {

  private static void expectReject(GameTestHelper helper, String expected, Runnable call) {
    try {
      call.run();
      helper.fail("transfer was accepted where it had to be refused (" + expected + ")");
    } catch (IllegalArgumentException e) {
      String message = String.valueOf(e.getMessage());
      helper.assertTrue(
          message.startsWith("message.guidedrailways.transfer."),
          "refusal must be a translatable transfer key, was " + message);
      helper.assertTrue(
          message.endsWith(expected),
          "expected refusal " + expected + " but was " + message);
    }
  }

  @GameTest(
      template = "empty",
      templateNamespace = "guidedrailways_signal_validation",
      timeoutTicks = 200)
  public static void refusesTransferWithinOneDimension(GameTestHelper helper) {
    var overworld = helper.getLevel();
    var at = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
    expectReject(
        helper,
        "destination",
        () -> PhysicalTransfer.transfer(overworld, UUID.randomUUID(), overworld, at));
    helper.succeed();
  }

  @GameTest(
      template = "empty",
      templateNamespace = "guidedrailways_signal_validation",
      timeoutTicks = 200)
  public static void refusesTransferOfAnUnknownBody(GameTestHelper helper) {
    var overworld = helper.getLevel();
    var nether = overworld.getServer().getLevel(Level.NETHER);
    helper.assertTrue(nether != null, "the test server must provide a nether");
    var at = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
    // Either the layout gate or the missing-body gate may come first depending on how the fixture world initialises
    // its containers; both are refusals that happen before anything can be moved.
    try {
      PhysicalTransfer.transfer(overworld, UUID.randomUUID(), nether, at);
      helper.fail("transfer of an unknown body was accepted");
    } catch (IllegalArgumentException e) {
      String message = String.valueOf(e.getMessage());
      helper.assertTrue(
          message.startsWith("message.guidedrailways.transfer."),
          "refusal must be a translatable transfer key, was " + message);
      helper.assertTrue(
          message.endsWith("missing") || message.endsWith("layout") || message.endsWith("unavailable"),
          "unexpected refusal for an unknown body: " + message);
    }
    helper.succeed();
  }

  private PortalTransferServerTests() {}
}
