package com.guidedrailways;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Every console block state combination has to resolve to a model, and every model to the textures it
 * names. A missing variant does not fail the build on its own: the client logs an
 * "Unable to load model" warning and draws nothing, which is how a screen silently stops updating.
 */
public final class ConsoleAssetCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static String read(String path) {
    try (InputStream in = ConsoleAssetCheck.class.getResourceAsStream(path)) {
      if (in == null) return null;
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception failure) {
      throw new IllegalStateException(path, failure);
    }
  }

  /** True when the resource exists and is not empty; textures are binary, so only size matters. */
  private static boolean packaged(String path) {
    try (InputStream in = ConsoleAssetCheck.class.getResourceAsStream(path)) {
      return in != null && in.readAllBytes().length > 8;
    } catch (Exception failure) {
      throw new IllegalStateException(path, failure);
    }
  }

  private static String modelPath(String id) {
    // "guidedrailways:block/driver_console_s1" -> /assets/guidedrailways/models/block/driver_console_s1.json
    var split = id.split(":", 2);
    return "/assets/" + split[0] + "/models/" + split[1] + ".json";
  }

  private static String texturePath(String id) {
    var split = id.split(":", 2);
    return "/assets/" + split[0] + "/textures/" + split[1] + ".png";
  }

  /** The element whose from[] starts with these x/z values, or null. */
  private static com.google.gson.JsonObject elementAt(com.google.gson.JsonObject model, double x, double z) {
    for (var element : model.getAsJsonArray("elements")) {
      var box = element.getAsJsonObject();
      var from = box.getAsJsonArray("from");
      if (Math.abs(from.get(0).getAsDouble() - x) < 1e-6
          && Math.abs(from.get(2).getAsDouble() - z) < 1e-6) return box;
    }
    return null;
  }
  public static void main(String[] args) {
    var blockstates = read("/assets/guidedrailways/blockstates/driver_console.json");
    check(blockstates != null, "the console block state file is packaged");
    var variants =
        JsonParser.parseString(blockstates).getAsJsonObject().getAsJsonObject("variants");
    check(
        variants.size() == 40,
        "four facings, five displays and pressed/unpressed must all be covered, found "
            + variants.size());

    Set<String> models = new LinkedHashSet<>();
    for (var entry : variants.entrySet()) {
      var variant = entry.getValue().getAsJsonObject();
      check(variant.has("model"), entry.getKey() + " names a model");
      models.add(variant.get("model").getAsString());
      if (entry.getKey().contains("pressed=true")) check(variant.has("y"), entry.getKey() + " rotates");
      else check(!entry.getKey().contains("pressed=false") || variant.has("y"), entry.getKey());
    }
    check(models.size() == 10, "five displays times pressed/unpressed, found " + models.size());

    Set<String> textures = new LinkedHashSet<>();
    for (var model : models) {
      var json = read(modelPath(model));
      check(json != null, "model packaged: " + model);
      var object = JsonParser.parseString(json).getAsJsonObject();
      check(
          object.getAsJsonArray("elements") != null || object.has("elements"),
          model + " keeps its geometry");
      var screen = object.getAsJsonObject("textures");
      check(screen != null && screen.has("screen"), model + " names a screen texture");
      textures.add(screen.get("screen").getAsString());
    }
    check(textures.size() == ConsoleDisplay.COUNT, "one screen texture per display, found " + textures.size());
    check(textures.size() == 5, "all five displays have their own texture");

    // The pressed variants have to light the button, not only lower it.
    Set<String> buttons = new LinkedHashSet<>();
    for (var model : models) {
      if (!model.endsWith("_pressed")) continue;
      var json = read(modelPath(model));
      var red =
          JsonParser.parseString(json)
              .getAsJsonObject()
              .getAsJsonObject("textures")
              .get("red")
              .getAsString();
      buttons.add(red);
    }
    // Pressed has to be both: lit and sunk. Checking only the texture let the sink be removed once
    // already, and the player expects to see the button move as well as flash.
    for (var model : models) {
      if (!model.endsWith("_pressed")) continue;
      var pressedJson = JsonParser.parseString(read(modelPath(model))).getAsJsonObject();
      var plain =
          JsonParser.parseString(read(modelPath(model.replace("_pressed", "")))).getAsJsonObject();
      var pressedButton = elementAt(pressedJson, 3.5, 10.5);
      var plainButton = elementAt(plain, 3.5, 10.5);
      check(pressedButton != null && plainButton != null, model + " keeps its button element");
      double pressedTop = pressedButton.getAsJsonArray("from").get(1).getAsDouble();
      double plainTop = plainButton.getAsJsonArray("from").get(1).getAsDouble();
      check(pressedTop < plainTop, model + " sinks the button when pressed");
      check(
          pressedTop > plainTop - 0.5,
          model + " sinks the button by a visible but not exaggerated amount");
    }
    check(buttons.size() == 1, "every pressed variant lights the same button texture");
    check(
        buttons.iterator().next().equals("guidedrailways:block/console_button_pressed"),
        "the pressed button uses its lit texture");
    check(packaged(texturePath("guidedrailways:block/console_button_pressed")), "lit button is packaged");
    for (var texture : textures) {
      check(texture.startsWith("guidedrailways:"), "screen textures stay in this mod: " + texture);
      check(packaged(texturePath(texture)), "texture packaged: " + texture);
    }
    System.out.println(
        "ConsoleAssetCheck: "
            + checks
            + " block state, model and texture coverage checks passed for 40 variants");
  }
}
