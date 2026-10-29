package com.guidedrailways;

import static com.guidedrailways.DriverMenu.Field.*;

import com.simibubi.create.foundation.gui.AllIcons;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** Settings and gear profiles share one server menu; neither page contains inventory slots. */
public final class DriverScreen extends AbstractContainerScreen<DriverMenu> {
  private Button forward,
      reverse,
      autoStart,
      autoPause,
      autoSkip,
      autoRestart,
      takeover,
      autoRemove,
      gearTabButton,
      saveGears,
      nameTabButton,
      saveName;
  private final List<AbstractWidget> mainWidgets = new ArrayList<>();
  private final EditBox[] gears = new EditBox[DriverModel.MAX_NOTCH];
  private final int[] syncedGears = new int[DriverModel.MAX_NOTCH];
  private boolean gearTab, gearsEdited;
  private boolean nameTab, nameEdited, namePending, nameFailed, nameSaved;
  private EditBox trainName;
  private String syncedName;
  private int selectedGear;
  private GearLeverWidget lever;
  private GearSpeedSlider speedSlider;

  DriverScreen(DriverMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 300;
    imageHeight = 240;
  }

  private static Component text(String key, Object... args) {
    return Component.translatable("gui.guidedrailways.driver." + key, args);
  }

  private CreateActionButton button(
      Component label, int x, int y, int width, Button.OnPress press) {
    return addRenderableWidget(
        new CreateActionButton(leftPos + x, topPos + y, width, label, press));
  }

  private CreateActionButton command(Component label, int x, int y, int width, int id) {
    var button =
        button(
            label,
            x,
            y,
            width,
            b -> {
              if (minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            });
    mainWidgets.add(button);
    return button;
  }

  @Override
  protected void init() {
    super.init();
    mainWidgets.clear();
    forward = command(text("forward"), 12, 76, 133, 3).selected(() -> menu.value(DIRECTION) == 1);
    reverse = command(text("reverse"), 155, 76, 133, 5).selected(() -> menu.value(DIRECTION) == -1);
    autoStart = command(Component.translatable("gui.guidedrailways.auto.start"), 12, 168, 86, 30);
    autoPause = command(Component.translatable("gui.guidedrailways.auto.pause"), 107, 168, 86, 31);
    autoSkip = command(Component.translatable("gui.guidedrailways.auto.skip"), 202, 168, 86, 33);
    autoRestart =
        command(Component.translatable("gui.guidedrailways.auto.restart"), 12, 190, 86, 34);
    takeover = command(text("takeover"), 107, 190, 86, 35);
    autoRemove =
        command(Component.translatable("gui.guidedrailways.auto.remove"), 202, 190, 86, 32);
    command(Component.translatable("gui.guidedrailways.align"), 107, 212, 86, 40);
    nameTabButton =
        button(
                text("train_name"),
                180,
                8,
                18,
                b -> {
                  nameTab = !nameTab;
                  gearTab = false;
                  setFocused(nameTab ? trainName : null);
                })
            .icon(AllIcons.I_CONFIG_OPEN, true)
            .selected(() -> nameTab);
    gearTabButton =
        button(
            text("gear_settings"),
            202,
            8,
            86,
            b -> {
              if (nameTab) nameTab = false;
              else gearTab = !gearTab;
              setFocused(null);
            });
    String previousName = trainName == null ? "" : trainName.getValue();
    trainName = new EditBox(font, leftPos + 18, topPos + 65, 264, 14, text("train_name"));
    trainName.setMaxLength(TrainName.MAX_LENGTH);
    trainName.setBordered(false);
    trainName.setTextColor(CreateGuiTheme.TEXT);
    trainName.setTextColorUneditable(CreateGuiTheme.DIM);
    trainName.setValue(previousName);
    trainName.setResponder(
        value -> {
          nameEdited = true;
          nameFailed = nameSaved = false;
        });
    trainName.visible = nameTab;
    addRenderableWidget(trainName);
    saveName =
        button(text("apply_name"), 12, 211, 248, b -> applyName()).icon(AllIcons.I_CONFIRM, false);
    for (int i = 0; i < gears.length; i++) {
      String previous = gears[i] == null ? "" : gears[i].getValue();
      var box = new EditBox(font, leftPos + 212, topPos + 68, 70, 14, text("gear_label", i + 1));
      box.setMaxLength(5);
      box.setBordered(false);
      box.setTextColor(CreateGuiTheme.TEXT);
      box.setTextColorUneditable(CreateGuiTheme.DIM);
      box.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]?)?"));
      box.setValue(previous);
      box.setResponder(value -> gearsEdited = true);
      gears[i] = addRenderableWidget(box);
    }
    Arrays.fill(syncedGears, -1);
    lever =
        addRenderableWidget(
            new GearLeverWidget(
                leftPos + 12, topPos + 53, 86, 141, () -> selectedGear, this::selectGear));
    speedSlider =
        addRenderableWidget(
            new GearSpeedSlider(
                leftPos + 110,
                topPos + 104,
                178,
                this::selectedSpeed,
                this::sliderMaximum,
                this::dragSpeed));
    saveGears =
        button(text("apply_gears"), 12, 211, 248, b -> applyGears())
            .icon(AllIcons.I_CONFIRM, false);
    button(text("close_settings"), 270, 211, 18, b -> onClose()).icon(AllIcons.I_CONFIRM, true);
  }

  private void selectGear(int gear) {
    gears[selectedGear].setFocused(false);
    selectedGear = Math.clamp(gear, 0, gears.length - 1);
  }

  private int selectedSpeed() {
    try {
      int value = (int) Math.round(Double.parseDouble(gears[selectedGear].getValue()) * 10);
      if (SpeedSetting.valid(value)) return value;
    } catch (NumberFormatException ignored) {
    }
    return Math.max(SpeedSetting.MIN, menu.gear(selectedGear));
  }

  private int sliderMaximum() {
    int maximum = Math.max(SpeedSetting.MIN, menu.value(MAX_SPEED));
    for (var box : gears)
      try {
        int value = (int) Math.round(Double.parseDouble(box.getValue()) * 10);
        if (SpeedSetting.valid(value)) maximum = Math.max(maximum, value);
      } catch (NumberFormatException ignored) {
      }
    return maximum;
  }

  private void dragSpeed(int value) {
    int[] profile = enteredGears();
    if (profile == null) return;
    value = GearEditorModel.betweenNeighbours(value, selectedGear, profile);
    gears[selectedGear].setValue(String.format(Locale.ROOT, "%.1f", value / 10.0));
  }

  private int[] enteredGears() {
    int[] values = new int[gears.length];
    try {
      for (int i = 0; i < values.length; i++)
        values[i] = (int) Math.round(Double.parseDouble(gears[i].getValue()) * 10);
    } catch (NumberFormatException ignored) {
      return null;
    }
    return SpeedSetting.validGears(SpeedSetting.MAX, values) ? values : null;
  }

  private void applyGears() {
    var values = enteredGears();
    if (!saveGears.active || values == null) return;
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
        new DriverNetwork.SetGears(menu.containerId, values));
    gearsEdited = false;
    setFocused(null);
    Arrays.fill(syncedGears, -1);
  }

  private void applyName() {
    if (!saveName.active || namePending || menu.value(CAN_CONFIGURE) == 0) return;
    namePending = true;
    nameFailed = nameSaved = false;
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
        new DriverNetwork.SetName(menu.containerId, TrainName.normalize(trainName.getValue())));
    setFocused(null);
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    var reply = menu.nameResult;
    if (reply != null) {
      menu.nameResult = null;
      if (namePending) {
        namePending = false;
        nameFailed = !reply.applied();
        if (reply.applied()) {
          trainName.setValue(reply.name());
          syncedName = reply.name();
          nameEdited = false;
          nameSaved = true;
        }
      }
    }
    if (!nameEdited
        && !namePending
        && menu.details.menu() == menu.containerId
        && !menu.details.trainName().equals(syncedName)) {
      syncedName = menu.details.trainName();
      trainName.setValue(syncedName);
      nameEdited = nameSaved = false;
    }
    for (int i = 0; i < gears.length; i++) {
      int value = menu.gear(i);
      if (value > 0 && value != syncedGears[i] && !gearsEdited) {
        gears[i].setValue(String.format(Locale.ROOT, "%.1f", value / 10.0));
        gearsEdited = false;
      }
      syncedGears[i] = value;
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    CreateGuiTheme.panel(g, leftPos, topPos, imageWidth, imageHeight);
    g.drawString(
        font,
        CreateGuiTheme.fit(font, title.getString(), 158),
        leftPos + 12,
        topPos + 4,
        CreateGuiTheme.HEADER,
        false);
    if (nameTab) {
      g.drawString(font, text("train_name"), leftPos + 12, topPos + 42, CreateGuiTheme.TEXT, false);
      CreateGuiTheme.inset(g, leftPos + 12, topPos + 58, 276, 24);
      int y = topPos + 94;
      for (var line : UserText.lines(text("name_help"))) {
        g.drawString(font, line, leftPos + 12, y, CreateGuiTheme.DIM, false);
        y += 12;
      }
      if (nameFailed || menu.value(CAN_CONFIGURE) == 0)
        g.drawString(
            font, text("name_read_only"), leftPos + 12, topPos + 126, CreateGuiTheme.DIM, false);
      else if (nameSaved)
        g.drawString(
            font, text("name_saved"), leftPos + 12, topPos + 126, CreateGuiTheme.ACCENT, false);
      g.drawString(
          font,
          CreateGuiTheme.fit(font, ownerHeading().getString(), 276),
          leftPos + 12,
          topPos + 150,
          CreateGuiTheme.TEXT,
          false);
      return;
    }
    if (gearTab) {
      g.drawString(
          font, text("lever_instructions"), leftPos + 12, topPos + 36, CreateGuiTheme.TEXT, false);
      g.drawString(
          font,
          text("selected_gear", selectedGear + 1),
          leftPos + 110,
          topPos + 68,
          CreateGuiTheme.ACCENT,
          false);
      CreateGuiTheme.inset(g, leftPos + 208, topPos + 64, 78, 20);
      g.drawString(
          font, text("speed_units"), leftPos + 110, topPos + 90, CreateGuiTheme.TEXT, false);
      g.drawString(font, "0.1", leftPos + 110, topPos + 127, CreateGuiTheme.DIM, false);
      String max = String.format(Locale.ROOT, "%.1f", sliderMaximum() / 10.0);
      g.drawString(
          font, max, leftPos + 288 - font.width(max), topPos + 127, CreateGuiTheme.DIM, false);
      for (int i = 0; i < gears.length; i++) {
        int x = 108 + (i < 4 ? 0 : 92), y = 140 + (i % 4) * 14;
        if (i == selectedGear) CreateGuiTheme.inset(g, leftPos + x - 2, topPos + y - 2, 90, 13);
        g.drawString(
            font,
            "P" + (i + 1) + "  " + gears[i].getValue(),
            leftPos + x,
            topPos + y,
            i == selectedGear ? CreateGuiTheme.ACCENT : CreateGuiTheme.TEXT,
            false);
      }
      g.drawString(
          font,
          CreateGuiTheme.fit(font, text("gears_order").getString(), 276),
          leftPos + 12,
          topPos + 199,
          CreateGuiTheme.DIM,
          false);
      return;
    }
    CreateGuiTheme.inset(g, leftPos + 12, topPos + 29, 276, 40);
    if (menu.value(HAS_SCHEDULE) != 0)
      g.drawString(
          font,
          CreateGuiTheme.fit(font, scheduleHeading().getString(), 158),
          leftPos + 12,
          topPos + 19,
          CreateGuiTheme.DIM,
          false);
    CreateGuiTheme.inset(g, leftPos + 12, topPos + 102, 276, 57);
    g.drawString(
        font,
        text("actual", String.format(Locale.ROOT, "%+.2f", menu.value(ACTUAL_SPEED) / 100.0)),
        leftPos + 18,
        topPos + 35,
        CreateGuiTheme.ACCENT,
        false);
    g.drawString(
        font,
        CreateGuiTheme.fit(
            font,
            text(
                    "power",
                    String.format(Locale.ROOT, "%.2f", menu.value(AVAILABLE_SPEED) / 100.0),
                    menu.value(POWERED),
                    menu.value(SUPPORTS))
                .getString(),
            264),
        leftPos + 18,
        topPos + 53,
        CreateGuiTheme.TEXT,
        false);
    String target = menu.details.target();
    if (menu.schedulePreview.isEmpty()) AllIcons.I_SCROLL.render(g, leftPos + 17, topPos + 106);
    else g.renderItem(menu.schedulePreview, leftPos + 17, topPos + 106);
    g.drawString(
        font,
        CreateGuiTheme.fit(
            font,
            Component.translatable(
                    "gui.guidedrailways.auto.target", target.isEmpty() ? "—" : target)
                .getString(),
            246),
        leftPos + 36,
        topPos + 108,
        CreateGuiTheme.TEXT,
        false);
    if (menu.details.remainingTenths() >= 0) {
      String remaining =
          CreateGuiTheme.fit(
              font,
              Component.translatable(
                      "gui.guidedrailways.auto.remaining",
                      String.format(Locale.ROOT, "%.1f", menu.details.remainingTenths() / 10.0))
                  .getString(),
              122);
      g.drawString(
          font,
          remaining,
          leftPos + 282 - font.width(remaining),
          topPos + 143,
          CreateGuiTheme.DIM,
          false);
    }
    g.drawString(
        font,
        CreateGuiTheme.fit(
            font,
            Component.translatable("gui.guidedrailways.auto.state." + menu.value(SCHEDULE_STATUS))
                .getString(),
            264),
        leftPos + 18,
        topPos + 124,
        CreateGuiTheme.ACCENT,
        false);
    if (menu.value(HAS_SCHEDULE) != 0)
      g.drawString(
          font,
          CreateGuiTheme.fit(
              font,
              Component.translatable(
                      "gui.guidedrailways.auto.progress",
                      menu.value(SCHEDULE_ENTRY),
                      menu.value(SCHEDULE_TOTAL))
                  .getString(),
              136),
          leftPos + 18,
          topPos + 143,
          CreateGuiTheme.DIM,
          false);
    g.drawString(
        font,
        CreateGuiTheme.fit(
            font,
            text("configured_max", String.format(Locale.ROOT, "%.1f", menu.value(MAX_SPEED) / 10.0))
                .getString(),
            248),
        leftPos + 12,
        topPos + 217,
        CreateGuiTheme.TEXT,
        false);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {}

  private Component scheduleHeading() {
    return menu.details.title().isEmpty()
        ? Component.translatable(
            "gui.guidedrailways.auto.speed_limit", menu.details.throttlePercent())
        : Component.translatable("gui.guidedrailways.auto.title", menu.details.title());
  }

  private Component ownerHeading() {
    var owner = menu.details.owner();
    return text(
        "train_owner", owner.known() ? UserText.userInput(owner.label()) : text("owner_unknown"));
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    for (var widget : mainWidgets) widget.visible = !gearTab && !nameTab;
    gearTabButton.setMessage(text(gearTab || nameTab ? "back_settings" : "gear_settings"));
    nameTabButton.visible = !gearTab;
    trainName.visible = saveName.visible = nameTab;
    trainName.setEditable(menu.value(CAN_CONFIGURE) != 0 && !namePending);
    saveName.active =
        menu.value(CAN_CONFIGURE) != 0
            && !namePending
            && menu.details.menu() == menu.containerId
            && !TrainName.normalize(trainName.getValue()).equals(menu.details.trainName());
    for (int i = 0; i < gears.length; i++) {
      gears[i].visible = gearTab && i == selectedGear;
      gears[i].setEditable(menu.value(IS_PRIMARY) != 0 && menu.value(OPERATOR_AVAILABLE) != 0);
    }
    lever.visible = speedSlider.visible = gearTab;
    speedSlider.active =
        menu.value(IS_PRIMARY) != 0
            && menu.value(OPERATOR_AVAILABLE) != 0
            && enteredGears() != null;
    speedSlider.refresh();
    saveGears.visible = gearTab;
    saveGears.active =
        menu.value(IS_PRIMARY) != 0
            && menu.value(OPERATOR_AVAILABLE) != 0
            && enteredGears() != null;
    forward.active = reverse.active = menu.value(IS_OPERATOR) != 0 && menu.value(OBSERVING) == 0;
    boolean schedule = menu.value(HAS_SCHEDULE) != 0 && menu.value(IS_PRIMARY) != 0;
    autoStart.active = schedule && menu.value(SCHEDULE_RUNNING) == 0;
    autoPause.active = schedule && menu.value(SCHEDULE_RUNNING) != 0;
    autoSkip.active =
        schedule
            && menu.value(STOPPED) != 0
            && menu.value(SCHEDULE_STATUS) != ScheduleController.FINISHED;
    autoRestart.active = schedule && menu.value(STOPPED) != 0;
    takeover.active =
        menu.value(IS_PRIMARY) != 0
            && menu.value(OPERATOR_AVAILABLE) != 0
            && (menu.value(IS_OPERATOR) == 0 || menu.value(OBSERVING) != 0);
    autoRemove.active =
        menu.value(HAS_SCHEDULE) != 0
            && menu.value(STOPPED) != 0
            && menu.value(OPERATOR_AVAILABLE) != 0;
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
    if (nameTab && menu.details.owner().known() && isHovering(12, 146, 276, 17, x, y))
      g.renderTooltip(
          font,
          List.of(ownerHeading(), UserText.userInput(menu.details.owner().id().toString())),
          Optional.empty(),
          x,
          y);
    if (nameTab
        && !trainName.isFocused()
        && font.width(trainName.getValue()) > 264
        && isHovering(12, 58, 276, 24, x, y))
      g.renderTooltip(
          font, List.of(UserText.userInput(trainName.getValue())), Optional.empty(), x, y);
    if (!gearTab && !nameTab && menu.value(HAS_SCHEDULE) != 0 && isHovering(12, 19, 158, 9, x, y))
      g.renderTooltip(
          font,
          menu.details.title().isEmpty()
              ? List.of(scheduleHeading())
              : List.of(
                  scheduleHeading(),
                  Component.translatable(
                      "gui.guidedrailways.auto.speed_limit", menu.details.throttlePercent())),
          Optional.empty(),
          x,
          y);
    Component status =
        Component.translatable("gui.guidedrailways.auto.state." + menu.value(SCHEDULE_STATUS));
    if (!gearTab && !nameTab && font.width(status) > 264 && isHovering(18, 124, 264, 10, x, y))
      g.renderTooltip(font, List.of(status), Optional.empty(), x, y);
    Component target =
        Component.translatable("gui.guidedrailways.auto.target", menu.details.target());
    if (!gearTab
        && !nameTab
        && !menu.schedulePreview.isEmpty()
        && isHovering(17, 106, 265, 16, x, y)) g.renderTooltip(font, menu.schedulePreview, x, y);
    else if (!gearTab
        && !nameTab
        && font.width(target) > 246
        && isHovering(36, 108, 246, 10, x, y)
        && !menu.details.target().isEmpty())
      g.renderTooltip(font, List.of(target), Optional.empty(), x, y);
    if (!nameTab
        && !isDragging()
        && isHovering(12, gearTab ? 195 : 211, 248, gearTab ? 14 : 18, x, y))
      g.renderTooltip(font, UserText.lines(text("gears_help")), Optional.empty(), x, y);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (gearTab && button == 0)
      for (int i = 0; i < gears.length; i++)
        if (isHovering(106 + (i < 4 ? 0 : 92), 138 + (i % 4) * 14, 90, 13, x, y)) {
          selectGear(i);
          setFocused(lever);
          return true;
        }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    // Container screens consume drags for item slots instead of forwarding them to widgets.
    if (gearTab
        && button == 0
        && isDragging()
        && (getFocused() == lever || getFocused() == speedSlider))
      return getFocused().mouseDragged(x, y, button, dx, dy);
    return super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (key == GLFW.GLFW_KEY_ESCAPE) {
      if (gearTab || nameTab) {
        gearTab = nameTab = false;
        setFocused(null);
      } else {
        DriverClient.exit();
        onClose();
      }
      return true;
    }
    if (nameTab && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
      applyName();
      return true;
    }
    // Text such as E must not be interpreted as the inventory-close shortcut.
    if (nameTab && trainName.isFocused() && key != GLFW.GLFW_KEY_TAB)
      return trainName.keyPressed(key, scan, mods);
    if (gearTab && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
      applyGears();
      return true;
    }
    return super.keyPressed(key, scan, mods);
  }
}
