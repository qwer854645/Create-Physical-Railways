package com.guidedrailways;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Only a held physical handle consumes mouse input; there is no driving mode or movement capture. */
public final class DriverClient {
  private static BlockPos held;
  private static ConsoleInteraction.Part part = ConsoleInteraction.Part.NONE;
  private static int requestedNotch, ticks;
  private static double dragPixels;
  private static boolean wasEmergency;
  private DriverClient() {}

  static void register() {
    DriverNetwork.clientHandler = DriverClient::accept;
    NeoForge.EVENT_BUS.addListener(DriverClient::mouse);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, DriverClient::turn);
    NeoForge.EVENT_BUS.addListener(DriverClient::scroll);
    NeoForge.EVENT_BUS.addListener(DriverClient::tick);
    NeoForge.EVENT_BUS.addListener(DriverClient::hud);
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
  }
  private static void accept(DriverNetwork.State state) {
    if (state.active() && (held == null || !held.equals(state.pos()))) send(state.pos(), 19);
    if (!state.active() && held != null && held.equals(state.pos()) && part == ConsoleInteraction.Part.LEVER) clear();
  }
  private static void send(BlockPos pos, int command) {
    if (Minecraft.getInstance().getConnection() != null)
      PacketDistributor.sendToServer(new DriverNetwork.Command(pos, command));
  }
  private static void clear() {
    held = null; part = ConsoleInteraction.Part.NONE; ticks = 0; dragPixels = 0;
  }
  private static void release() {
    if (held != null && part == ConsoleInteraction.Part.LEVER) send(held, 19);
    clear();
  }
  private static DriverConsoleEntity target() {
    var mc = Minecraft.getInstance();
    return mc.player != null && mc.level != null && mc.screen == null
        && mc.hitResult instanceof BlockHitResult hit
        && mc.level.getBlockEntity(hit.getBlockPos()) instanceof DriverConsoleEntity console ? console : null;
  }
  private static boolean rightDown() {
    var mc = Minecraft.getInstance();
    return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
  }
  private static ConsoleInteraction.Part pick(DriverConsoleEntity console) {
    var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera();
    var eye = camera.getPosition(); var look = camera.getLookVector();
    var end = eye.add(look.x() * mc.player.blockInteractionRange(), look.y() * mc.player.blockInteractionRange(),
        look.z() * mc.player.blockInteractionRange());
    var body = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level, console.getBlockPos());
    var pose = body instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client ? client.renderPose() : null;
    float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
    return ConsoleInteraction.pick(console, eye, end, pose,
        net.minecraft.util.Mth.lerp(partial, console.previousLever, console.lever), false);
  }
  private static void mouse(InputEvent.MouseButton.Pre event) {
    if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
    if (event.getAction() == GLFW.GLFW_RELEASE) {
      if (held != null) { release(); event.setCanceled(true); }
      return;
    }
    if (event.getAction() != GLFW.GLFW_PRESS || Screen.hasShiftDown()) return;
    var console = target(); var mc = Minecraft.getInstance();
    if (console == null || !mc.player.getMainHandItem().isEmpty()) return;
    var next = pick(console);
    if (next == ConsoleInteraction.Part.NONE) return;
    held = console.getBlockPos(); part = next; ticks = 0; dragPixels = 0;
    wasEmergency = console.emergency; requestedNotch = console.notch;
    send(held, part == ConsoleInteraction.Part.LEVER ? 35 : 8);
    event.setCanceled(true);
  }
  private static void tick(ClientTickEvent.Post event) {
    if (held == null) return;
    var mc = Minecraft.getInstance(); var console = target();
    if (!rightDown() || !mc.isWindowActive() || console == null || !console.getBlockPos().equals(held)
        || Screen.hasShiftDown() || !mc.player.getMainHandItem().isEmpty()) { release(); return; }
    ticks++;
    if (part == ConsoleInteraction.Part.EMERGENCY) {
      if (pick(console) != part) { release(); return; }
      if (wasEmergency && ticks >= 20) { send(held, 9); release(); }
    } else if (ticks % 5 == 0) send(held, 23);
  }
  private static void turn(CalculatePlayerTurnEvent event) {
    var mc = Minecraft.getInstance();
    if (!DriverInputPolicy.suppressLook(held != null, part == ConsoleInteraction.Part.LEVER,
        mc.screen != null, rightDown())) return;
    var console = target();
    if (console == null || !console.getBlockPos().equals(held)) { release(); return; }
    if (!console.emergency) {
      dragPixels -= mc.mouseHandler.getYVelocity() / mc.getWindow().getGuiScale();
      int delta = (int) (dragPixels / 18);
      if (delta != 0) {
        int next = Math.clamp(requestedNotch + delta, -DriverModel.BRAKE_NOTCHES, DriverModel.MAX_NOTCH);
        dragPixels -= delta * 18;
        if (next != requestedNotch) { requestedNotch = next; send(held, DriverInputPolicy.NOTCH_BASE + next); }
      }
    }
    event.setCinematicCameraEnabled(false);
    event.setMouseSensitivity(DriverInputPolicy.heldMouseSensitivity());
  }
  private static void scroll(InputEvent.MouseScrollingEvent event) {
    if (part != ConsoleInteraction.Part.LEVER || held == null || !rightDown()) return;
    var console = target();
    if (console == null || !console.getBlockPos().equals(held)) return;
    event.setCanceled(true);
    if (!console.emergency && Math.abs(event.getScrollDeltaY()) > .01) {
      requestedNotch = DriverModel.step(requestedNotch, event.getScrollDeltaY() > 0 ? 1 : -1);
      send(held, DriverInputPolicy.NOTCH_BASE + requestedNotch);
    }
  }
  private static void hud(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance(); var console = target();
    if (console == null || mc.options.hideGui || !mc.player.getMainHandItem().isEmpty()) return;
    var aimed = held != null ? part : pick(console);
    String key = aimed == ConsoleInteraction.Part.LEVER ? "direct_lever"
        : aimed == ConsoleInteraction.Part.EMERGENCY ? console.emergency ? "direct_reset" : "direct_emergency" : null;
    if (key == null) return;
    var label = Component.translatable("gui.guidedrailways.driver." + key);
    int y = mc.getWindow().getGuiScaledHeight() / 2 + 18;
    for (var row : mc.font.split(label, Math.max(40, mc.getWindow().getGuiScaledWidth() - 24))) {
      event.getGuiGraphics().drawString(mc.font, row, (mc.getWindow().getGuiScaledWidth() - mc.font.width(row)) / 2, y, CreateGuiTheme.TEXT);
      y += mc.font.lineHeight + 2;
    }
  }
}
