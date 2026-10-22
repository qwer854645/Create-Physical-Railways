package com.guidedrailways;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Only sneak-right settings creates a menu. Driving is independent of menu lifetime. */
public final class DriverMenu extends AbstractContainerMenu {
  enum Field {
    DIRECTION,
    ACTUAL_SPEED,
    AVAILABLE_SPEED,
    SUPPORTS,
    POWERED,
    IS_OPERATOR,
    HAS_SCHEDULE,
    SCHEDULE_STATUS,
    SCHEDULE_ENTRY,
    SCHEDULE_TOTAL,
    IS_PRIMARY,
    SCHEDULE_RUNNING,
    STOPPED,
    OBSERVING,
    OPERATOR_AVAILABLE,
    MAX_SPEED,
    CAN_CONFIGURE;
  }

  private static final Field[] FIELDS = Field.values();
  private static final int GEAR_OFFSET = FIELDS.length;
  private static final int DATA_COUNT = GEAR_OFFSET + DriverModel.MAX_NOTCH;

  private final DriverConsoleEntity console;
  private final ContainerData data;
  private final Player viewer;
  private long nextDetails;
  private DriverNetwork.MenuState sentDetails;
  DriverNetwork.MenuState details =
      new DriverNetwork.MenuState(-1, "", -1, "", 100, "", TrainOwner.UNKNOWN);
  DriverNetwork.NameResult nameResult;
  ItemStack schedulePreview = ItemStack.EMPTY;
  private ItemStack sentSchedule;

  DriverMenu(int id, Inventory inventory, DriverConsoleEntity console) {
    super(Content.DRIVER_MENU.get(), id);
    this.console = console;
    viewer = inventory.player;
    data =
        console == null
            ? new SimpleContainerData(DATA_COUNT)
            : new ContainerData() {
              @Override
              public int get(int i) {
                if (i >= GEAR_OFFSET && i < DATA_COUNT)
                  return console.gearSpeeds()[i - GEAR_OFFSET];
                return switch (FIELDS[i]) {
                  case DIRECTION -> console.direction;
                  case ACTUAL_SPEED -> (int) Math.clamp(console.actual * 100, -32000, 32000);
                  case AVAILABLE_SPEED -> (int) Math.clamp(console.available * 100, 0, 32000);
                  case SUPPORTS -> console.supports;
                  case POWERED -> console.powered;
                  case IS_OPERATOR ->
                      inventory.player.getUUID().equals(console.operator)
                              && DriverControls.primaryView(console)
                          ? 1
                          : 0;
                  case HAS_SCHEDULE -> console.schedule.present() ? 1 : 0;
                  case SCHEDULE_STATUS ->
                      DriverControls.primaryView(console) ? console.schedule.status() : 15;
                  case SCHEDULE_ENTRY ->
                      Math.min(console.schedule.total(), console.schedule.entry() + 1);
                  case SCHEDULE_TOTAL -> console.schedule.total();
                  case IS_PRIMARY -> DriverControls.primaryView(console) ? 1 : 0;
                  case SCHEDULE_RUNNING ->
                      console.schedule.running() || console.schedule.resumePending() ? 1 : 0;
                  case STOPPED -> DriverControls.stopped(console) ? 1 : 0;
                  case OBSERVING -> console.observing ? 1 : 0;
                  case OPERATOR_AVAILABLE ->
                      console.operator == null
                              || inventory.player.getUUID().equals(console.operator)
                          ? 1
                          : 0;
                  case MAX_SPEED -> console.maxSpeedTenths();
                  case CAN_CONFIGURE -> console.canConfigureView(inventory.player) ? 1 : 0;
                };
              }

              @Override
              public void set(int i, int value) {}

              @Override
              public int getCount() {
                return DATA_COUNT;
              }
            };
    addDataSlots(data);
  }

  int value(Field field) {
    return data.get(field.ordinal());
  }

  int gear(int index) {
    return data.get(GEAR_OFFSET + index);
  }

  void setGears(Player player, int[] tenths) {
    if (console != null && stillValid(player) && console.setGearSpeeds(player, tenths))
      broadcastChanges();
  }

  void setName(net.minecraft.server.level.ServerPlayer player, String value) {
    boolean applied = console != null && stillValid(player) && console.setTrainName(player, value);
    String actual =
        console == null
            ? ""
            : applied ? console.trainName() : DriverControls.primaryForView(console).trainName();
    nextDetails = 0;
    broadcastChanges();
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        player, new DriverNetwork.NameResult(containerId, applied, actual));
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (console == null
        || !(viewer instanceof net.minecraft.server.level.ServerPlayer player)
        || !stillValid(player)) return;
    long tick = player.serverLevel().getGameTime();
    if (tick < nextDetails) return;
    nextDetails = tick + 5;
    if (sentSchedule == null || !ItemStack.matches(sentSchedule, console.schedule.item())) {
      sentSchedule = console.schedule.item().copy();
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
          player, new DriverNetwork.SchedulePreview(containerId, sentSchedule.copy()));
    }
    String target = console.schedule.target();
    if (target.length() > 256) target = target.substring(0, 256);
    int distance =
        Double.isFinite(console.schedule.remaining())
            ? (int)
                Math.clamp(
                    Math.round(Math.max(0, console.schedule.remaining()) * 10),
                    0,
                    Integer.MAX_VALUE)
            : -1;
    String title = console.schedule.currentTitle();
    if (title.length() > 256) title = title.substring(0, 256);
    var primary = DriverControls.primaryForView(console);
    var next =
        new DriverNetwork.MenuState(
            containerId,
            target,
            distance,
            title,
            (int) Math.round(console.schedule.throttle() * 100),
            primary.trainName(),
            primary.trainOwner());
    if (!next.equals(sentDetails)) {
      sentDetails = next;
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, next);
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return console == null || DriverControls.accessible(console, player);
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (console == null || !stillValid(player)) return false;
    if (id >= 30 && id <= 34) {
      boolean result = console.schedule.action(player, id);
      nextDetails = 0;
      broadcastChanges();
      return result;
    }
    if (id != 35 && id != 3 && id != 5) return false;
    console.command(player, id);
    broadcastChanges();
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }
}
