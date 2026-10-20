package com.guidedrailways;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Development measurement of installed renderers, at the main menu. Never creates a world or
 * physics server.
 */
final class NativeBogeyCapture {
  private record Entry(BogeyStyle style, BogeySizes.BogeySize size) {}

  private static List<Entry> entries;
  private static int next;
  private static final JsonArray results = new JsonArray();
  private static boolean done;

  static void register() {
    if (!Boolean.getBoolean("guidedrailways.captureBogeys")) return;
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
          var mc = Minecraft.getInstance();
          if (done || mc.getOverlay() != null || mc.screen == null) return;
          try {
            if (entries == null) {
              entries = new ArrayList<>();
              AllBogeyStyles.BOGEY_STYLES.values().stream()
                  .filter(
                      s ->
                          s.id.getNamespace().equals("railways")
                              || s.id.getNamespace().equals("create_bb")
                              || s == AllBogeyStyles.STANDARD)
                  .sorted(Comparator.comparing(s -> s.id.toString()))
                  .forEach(
                      s ->
                          s.validSizes().stream()
                              .sorted(Comparator.comparing(z -> z.id().toString()))
                              .forEach(z -> entries.add(new Entry(s, z))));
            }
            if (next < entries.size()) {
              capture(entries.get(next++));
              return;
            }
            var path =
                java.nio.file.Path.of(System.getProperty("guidedrailways.captureBogeysOutput"));
            java.nio.file.Files.writeString(path, new Gson().toJson(results));
            done = true;
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .info("[BogeyCapture] COMPLETE entries={} output={}", results.size(), path);
          } catch (Throwable error) {
            done = true;
            org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
                .error("[BogeyCapture] FAILED", error);
          }
        });
  }

  private static final class Recorder implements VertexConsumer {
    final List<double[]> vertices = new ArrayList<>();

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      vertices.add(new double[] {x, y, z});
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this;
    }
  }

  private static void capture(Entry entry) {
    var style = entry.style;
    var size = entry.size;
    var block = style.getBlockForSize(size);
    var row = new JsonObject();
    row.addProperty("id", style.id.toString());
    row.addProperty("size", size.id().toString());
    row.addProperty("gauge", block.getTrackType(style).id.toString());
    row.addProperty("wheelRadius", block.getWheelRadius());
    row.addProperty("spacing", block.getWheelPointSpacing());
    var anchor = block.getConnectorAnchorOffset(false);
    row.add("connector", new Gson().toJsonTree(new double[] {anchor.x, anchor.y, anchor.z}));
    var frames = new JsonArray();
    for (int phase = 0; phase < 12; phase++) {
      var recorder = new Recorder();
      MultiBufferSource buffers = type -> recorder;
      style.render(
          size,
          0,
          new PoseStack(),
          buffers,
          0xF000F0,
          0,
          phase * 30,
          style.defaultData.copy(),
          false);
      frames.add(new Gson().toJsonTree(recorder.vertices));
    }
    row.add("frames", frames);
    results.add(row);
    org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID)
        .info(
            "[BogeyCapture] {}/{} id={} size={} vertices={}",
            next,
            entries.size(),
            style.id,
            size.id(),
            frames.get(0).getAsJsonArray().size());
  }
}
