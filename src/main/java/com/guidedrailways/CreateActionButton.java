package com.guidedrailways;

import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.gui.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

/** Text actions share Create's native 18px button states, not vanilla button sprites. */
final class CreateActionButton extends Button {
  private AllIcons icon;
  private boolean iconOnly;
  private BooleanSupplier selected = () -> false;

  CreateActionButton(int x, int y, int width, Component label, OnPress press) {
    super(x, y, width, 18, label, press, DEFAULT_NARRATION);
    refreshTooltip();
  }

  CreateActionButton icon(AllIcons icon, boolean only) {
    this.icon = icon;
    iconOnly = only;
    return this;
  }

  CreateActionButton selected(BooleanSupplier value) {
    selected = value;
    return this;
  }

  @Override
  public void setMessage(Component message) {
    if (java.util.Objects.equals(getMessage(), message)) return;
    super.setMessage(message);
    refreshTooltip();
  }

  private boolean tooltipNeeded;

  private void refreshTooltip() {
    boolean needed =
        iconOnly
            || Minecraft.getInstance().font.width(getMessage()) > width - (icon == null ? 8 : 28);
    tooltipNeeded = needed;
    setTooltip(needed ? Tooltip.create(getMessage()) : null);
  }

  @Override
  protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
    var texture =
        !active
            ? AllGuiTextures.BUTTON_DISABLED
            : isHoveredOrFocused() && AllKeys.isMouseButtonDown(0)
                ? AllGuiTextures.BUTTON_DOWN
                : isHoveredOrFocused()
                    ? AllGuiTextures.BUTTON_HOVER
                    : selected.getAsBoolean() ? AllGuiTextures.BUTTON_GREEN : AllGuiTextures.BUTTON;
    CreateGuiTheme.button(g, texture, getX(), getY(), width);
    var font = Minecraft.getInstance().font;
    if (tooltipNeeded != (iconOnly || font.width(getMessage()) > width - (icon == null ? 8 : 28)))
      refreshTooltip();
    String label =
        iconOnly
            ? ""
            : CreateGuiTheme.fit(font, getMessage().getString(), width - (icon == null ? 8 : 28));
    int total = font.width(label) + (icon == null ? 0 : iconOnly ? 16 : 20),
        left = getX() + (width - total) / 2;
    if (icon != null) icon.render(g, left, getY() + 1);
    if (!label.isEmpty())
      g.drawString(
          font,
          label,
          left + (icon == null ? 0 : 20),
          getY() + 5,
          active ? 0xffeeeeee : 0xff666666,
          active);
  }
}
