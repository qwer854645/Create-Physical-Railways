package com.physicalrailways;

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
    var assets = Path.of("src/main/resources/assets/physicalrailways");
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
            "PhysicalRailwayScenes.java", "ElectricTrainScene.java", "RailTransferScene.java", "RailwayOperationScenes.java")) {
      var source = Files.readString(Path.of("src/main/java/com/physicalrailways", file));
      var matches = textCall.matcher(source);
      while (matches.find()) {
        var key = matches.group(1);
        used.add(key);
        check(shared.containsKey(key), "storyboard text is registered: " + key);
        check(
            en.has("physicalrailways.ponder.shared." + key)
                && zh.has("physicalrailways.ponder.shared." + key),
            "bilingual storyboard text: " + key);
      }
    }
    check(used.equals(shared.keySet()), "no orphaned or missing shared tutorial text");
    var plugin = Files.readString(Path.of("src/main/java/com/physicalrailways/RailwayPonderPlugin.java"));
    var registered = new HashSet<String>();
    var registrations = Pattern.compile("addStoryBoard\\((?:id\\(\"[^\"]+\"\\),\\s*)?\"([^\"]+)\"").matcher(plugin);
    while (registrations.find()) check(registered.add(registrations.group(1)), "each storyboard registered once");
    check(registered.size() == 11, "all eleven teaching pages are registered");
    for (var key : used) {
      int ticks = RailwayPonderPlugin.captionTicks(key);
      check(ticks >= 100 && ticks <= 240, "bounded reading time: " + key);
      if (shared.get(key).split("\\s+").length > 30) check(ticks > 150, "long captions get extra reading time");
    }

    for (var name :
        List.of("physical_bogey", "physical_controls", "electric_controls", "rail_transfer", "physical_coupling", "physical_settings", "physical_schedule", "physical_conditions", "physical_network", "physical_cargo", "physical_variants")) {
      check(registered.remove(name), "validated template is registered: " + name);
      var header = "physicalrailways.ponder." + name + ".header";
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
        if (id.equals("physicalrailways:bogie_frame")) {
          wheels++;
          check(
              (xyz.equals(List.of(4, 5, 4)) || name.equals("physical_coupling") && xyz.equals(List.of(4, 5, 7))), "separate bogey storage matches the animated section");
        }
        if (id.equals("physicalrailways:bogie_mount")) mounts++;
        if (id.equals("physicalrailways:rail_docking_connector")) docks++;
      }
      check(rails == 9 && wheels == (name.equals("physical_coupling") ? 2 : 1) && mounts == (name.equals("physical_coupling") ? 2 : 1), "each scene has a track, bogey and mount");
      check(
          docks == (name.equals("rail_transfer") ? 2 : 0),
          "optional docking assets stay in their own scene");
    }
    check(registered.isEmpty(), "no registered page is missing a validated template");
    System.out.println("PonderAssetCheck: " + checks + " template and localization checks passed");
  }
}
