package com.guidedrailways;

import com.google.gson.*;
import java.util.*;

/** Current built-in and native geometry catalog. No historical layout migrations. */
public final class BogeyVariant {
  public static final BogeyVariant DEFAULT = new BogeyVariant();
  private static final List<BogeyVariant> ALL = new ArrayList<>();
  public final String id, size;
  public final RailGauge gauge;
  public final double renderY, tread, ride, half, animationRatio, supportBottom;
  public final int mountTop;
  public final int compactMountTop;
  final List<double[]> compactPlate;
  final List<double[]> boxes;
  private final int index;

  private BogeyVariant() {
    index = 0;
    id = "";
    size = "";
    gauge = RailGauge.STANDARD;
    renderY = BogieGeometry.RENDER_Y;
    tread = BogieGeometry.TREAD_RADIUS;
    ride = BogieGeometry.RIDE_HEIGHT;
    half = 1;
    mountTop = 2;
    compactMountTop = 2;
    compactPlate = List.of(new double[] {0, .75, 0, 1, 1, 1});
    supportBottom = BogieGeometry.SUPPORT_BOTTOM;
    animationRatio = 1;
    boxes = List.of();
  }

  private BogeyVariant(JsonObject data) {
    index = data.get("index").getAsInt();
    id = data.get("id").getAsString();
    size = data.get("size").getAsString();
    gauge = Objects.requireNonNull(RailGauge.forTrack(data.get("gauge").getAsString()));
    renderY = data.get("renderY").getAsDouble();
    tread = data.get("tread").getAsDouble();
    ride = data.get("ride").getAsDouble();
    half = data.get("half").getAsDouble();
    mountTop = data.get("mountTop").getAsInt();
    compactMountTop = data.get("compactMountTop").getAsInt();
    var plate = new ArrayList<double[]>();
    for (var value : data.getAsJsonArray("compactPlate")) {
      double[] b = new double[6];
      for (int i = 0; i < 6; i++) b[i] = value.getAsJsonArray().get(i).getAsDouble();
      plate.add(b);
    }
    compactPlate = List.copyOf(plate);
    animationRatio = data.get("animationRatio").getAsDouble();
    supportBottom = data.get("supportBottom").getAsDouble();
    var list = new ArrayList<double[]>();
    for (var value : data.getAsJsonArray("boxes")) {
      double[] b = new double[6];
      for (int i = 0; i < 6; i++) b[i] = value.getAsJsonArray().get(i).getAsDouble();
      list.add(b);
    }
    boxes = List.copyOf(list);
  }

  static {
    ALL.add(DEFAULT);
    try (var stream =
        BogeyVariant.class.getResourceAsStream("/assets/guidedrailways/geometry/all_bogeys.json")) {
      for (var row :
          JsonParser.parseReader(
                  new java.io.InputStreamReader(
                      Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonArray()) {
        var v = new BogeyVariant(row.getAsJsonObject());
        if (v.index != ALL.size()) throw new IllegalStateException("Non-contiguous bogey catalog");
        ALL.add(v);
      }
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  public int ordinal() {
    return index;
  }

  public static BogeyVariant[] values() {
    return ALL.toArray(BogeyVariant[]::new);
  }

  public static BogeyVariant read(int index) {
    return index >= 0 && index < ALL.size() ? ALL.get(index) : DEFAULT;
  }

  public static BogeyVariant find(String id, String size) {
    for (var v : ALL) if (v.id.equals(id) && v.size.equals(size)) return v;
    return null;
  }

  public static BogeyVariant defaultFor(RailGauge gauge) {
    return Objects.requireNonNull(
        find(
            switch (gauge) {
              case NARROW -> "railways:narrow_default";
              case WIDE -> "railways:wide_default";
              case MONORAIL -> "railways:monobogey";
              default -> "create:standard";
            },
            "create:small"));
  }

  public double plateOffset() {
    return mountTop - .5 - ride;
  }

  public double compactPlateOffset() {
    return compactMountTop - .5 - ride;
  }

  public double compactPivotHeight() {
    return compactPlateOffset() + BogieGeometry.MOUNT_UNDERSIDE - .5;
  }

  public double pivotHeight() {
    return plateOffset() + BogieGeometry.MOUNT_UNDERSIDE - .5;
  }

  @Override
  public String toString() {
    return this == DEFAULT ? "DEFAULT" : id + "/" + size;
  }
}
