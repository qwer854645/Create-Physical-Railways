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

  public static void main(String[] args) {
    var blockstates = read("/assets/guidedrailways/blockstates/driver_console.json");
    check(blockstates != null, "the console block state file is packaged");
    var variants =
        JsonParser.parseString(blockstates).getAsJsonObject().getAsJsonObject("variants");
    check(
        variants.size() == 32,
        "four facings, four displays and pressed/unpressed must all be covered, found "
            + variants.size());

    Set<String> models = new LinkedHashSet<>();
    for (var entry : variants.entrySet()) {
      var variant = entry.getValue().getAsJsonObject();
      check(variant.has("model"), entry.getKey() + " names a model");
      models.add(variant.get("model").getAsString());
      if (entry.getKey().contains("pressed=true")) check(variant.has("y"), entry.getKey() + " rotates");
      else check(!entry.getKey().contains("pressed=false") || variant.has("y"), entry.getKey());
    }
    check(models.size() == 8, "four displays times pressed/unpressed, found " + models.size());

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
    check(textures.size() == 4, "all four displays have their own texture");

    for (var texture : textures) {
      check(texture.startsWith("guidedrailways:"), "screen textures stay in this mod: " + texture);
      check(packaged(texturePath(texture)), "texture packaged: " + texture);
    }
    System.out.println(
        "ConsoleAssetCheck: "
            + checks
            + " block state, model and texture coverage checks passed for 32 variants");
  }
}
