package com.guidedrailways;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.createmod.ponder.api.registration.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** All storyboards are client-side illustrations; they never assemble a real physics body. */
final class RailwayPonderPlugin implements PonderPlugin {
  private static final String SHARED = GuidedRailways.ID + ".ponder.shared.";
  private static final JsonObject ENGLISH = loadEnglish();

  @Override
  public String getModId() {
    return GuidedRailways.ID;
  }

  @Override
  public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
    helper.forComponents(id("bogie")).addStoryBoard("physical_bogey", PhysicalRailwayScenes::bogey);
    helper
        .forComponents(id("driver_console"))
        .addStoryBoard("physical_controls", PhysicalRailwayScenes::controls);
    if (ModList.get().isLoaded("electroenergetics"))
      helper
          .addStoryBoard(id("driver_console"), "electric_controls", ElectricTrainScene::electric)
          .orderAfter("physical_controls");
    if (ModList.get().isLoaded("simulated"))
      helper
          .forComponents(id("rail_docking_connector"))
          .addStoryBoard("rail_transfer", RailTransferScene::transfer);
  }

  @Override
  public void registerSharedText(SharedTextRegistrationHelper helper) {
    for (var entry : ENGLISH.entrySet())
      if (entry.getKey().startsWith(SHARED))
        helper.registerSharedText(
            entry.getKey().substring(SHARED.length()), entry.getValue().getAsString());
  }

  static String title(String scene) {
    return Objects.requireNonNull(ENGLISH.get(GuidedRailways.ID + ".ponder." + scene + ".header"))
        .getAsString();
  }

  private static JsonObject loadEnglish() {
    try (var reader =
        new InputStreamReader(
            Objects.requireNonNull(
                RailwayPonderPlugin.class.getResourceAsStream(
                    "/assets/guidedrailways/lang/en_us.json")),
            StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot load Ponder fallback text", error);
    }
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath(GuidedRailways.ID, name);
  }
}
