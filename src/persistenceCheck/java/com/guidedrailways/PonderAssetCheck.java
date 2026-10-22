package com.guidedrailways;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.nbt.*;

/** Real template decoding and registered text checks; does not start a client or a world. */
public final class PonderAssetCheck {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    var assets = Path.of("src/main/resources/assets/guidedrailways");
    var en =
        JsonParser.parseString(Files.readString(assets.resolve("lang/en_us.json")))
            .getAsJsonObject();
    var zh =
        JsonParser.parseString(Files.readString(assets.resolve("lang/zh_cn.json")))
            .getAsJsonObject();
    var shared = new HashMap<String, String>();
    new RailwayPonderPlugin()
        .registerSharedText(
            (key, text) -> {
              check(shared.putIfAbsent(key, text) == null, "unique registered Ponder text: " + key);
            });
    var used = new HashSet<String>();
    var textCall = Pattern.compile("text\\(\\s*scene\\s*,\\s*\"([a-z_]+)\"");
    for (var file :
        List.of(
            "PhysicalRailwayScenes.java", "ElectricTrainScene.java", "RailTransferScene.java")) {
      var source = Files.readString(Path.of("src/main/java/com/guidedrailways", file));
      var matches = textCall.matcher(source);
      while (matches.find()) {
        var key = matches.group(1);
        used.add(key);
        check(shared.containsKey(key), "storyboard text is registered: " + key);
        check(
            en.has("guidedrailways.ponder.shared." + key)
                && zh.has("guidedrailways.ponder.shared." + key),
            "bilingual storyboard text: " + key);
      }
    }
    check(used.equals(shared.keySet()), "no orphaned or missing shared tutorial text");
    for (var name :
        List.of("physical_bogey", "physical_controls", "electric_controls", "rail_transfer")) {
      var header = "guidedrailways.ponder." + name + ".header";
      check(
          zh.has(header) && RailwayPonderPlugin.title(name).equals(en.get(header).getAsString()),
          "scene title uses the English fallback and localized resources: " + name);
      var template =
          NbtIo.readCompressed(
              assets.resolve("ponder/" + name + ".nbt"), NbtAccounter.unlimitedHeap());
      var size = template.getList("size", Tag.TAG_INT);
      check(
          size.size() == 3 && size.getInt(0) == 9 && size.getInt(1) == 7 && size.getInt(2) == 9,
          "valid template bounds: " + name);
      var palette = template.getList("palette", Tag.TAG_COMPOUND);
      var positions = new HashSet<List<Integer>>();
      int rails = 0, wheels = 0, mounts = 0, docks = 0;
      for (var value : template.getList("blocks", Tag.TAG_COMPOUND)) {
        var block = (CompoundTag) value;
        var pos = block.getList("pos", Tag.TAG_INT);
        check(pos.size() == 3, "block has three coordinates");
        var xyz = List.of(pos.getInt(0), pos.getInt(1), pos.getInt(2));
        check(positions.add(xyz), "no overlapping stored blocks: " + name + xyz);
        for (int axis = 0; axis < 3; axis++)
          check(xyz.get(axis) >= 0 && xyz.get(axis) < size.getInt(axis), "block within template");
        check(
            block.getInt("state") >= 0 && block.getInt("state") < palette.size(),
            "valid palette index");
        var state = palette.getCompound(block.getInt("state"));
        var id = state.getString("Name");
        if (id.equals("create:track")) {
          rails++;
          check(
              state.getCompound("Properties").getString("shape").equals("zo"),
              "rails match scene travel axis");
        }
        if (id.equals("guidedrailways:bogie_frame")) {
          wheels++;
          check(
              xyz.equals(List.of(4, 5, 4)), "separate bogey storage matches the animated section");
        }
        if (id.equals("guidedrailways:bogie_mount")) mounts++;
        if (id.equals("guidedrailways:rail_docking_connector")) docks++;
      }
      check(rails == 9 && wheels == 1 && mounts == 1, "each scene has a track, bogey and mount");
      check(
          docks == (name.equals("rail_transfer") ? 2 : 0),
          "optional docking assets stay in their own scene");
    }
    System.out.println("PonderAssetCheck: " + checks + " template and localization checks passed");
  }
}
