package com.guidedrailways;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

public final class NativeCouplerModelCheck {
  private static int checks;
  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
  private static JsonObject json(ZipFile jar, String namespace, String path) throws IOException {
    var entry = jar.getEntry("assets/" + namespace + "/" + path + ".json");
    if (entry == null) throw new FileNotFoundException(path);
    try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }
  public static void main(String[] args) throws Exception {
    int models = 0;
    try (var jar = new ZipFile(args[0])) {
      NativeCouplerModel.Source source = (namespace, path) -> json(jar, namespace, path);
      for (var kind : CouplerKind.values()) {
        if (kind == CouplerKind.STANDARD) continue;
        for (String mount : new String[] {"single_deco", "wooden_headstock", "copycat_headstock"}) {
          for (boolean flip : new boolean[] {false, true}) {
            if (flip && mount.equals("single_deco")) continue;
            var path = NativeCouplerModel.path(mount, kind, flip);
            check(NativeCouplerModel.kind(path) == kind, "kind/path agree");
            var raw = source.read("railways", "models/block/buffer/" + path);
            var model = NativeCouplerModel.resolve(raw, source);
            var pieces = NativeCouplerModel.pieces(model, kind, !mount.equals("single_deco"));
            check(pieces.size() == model.getAsJsonArray("elements").size(), "one shared piece per model element");
            check(pieces.stream().anyMatch(NativeCouplerModel.Piece::attachment), "coupler has rendered hardware");
            for (int i = 0; i < pieces.size(); i++) {
              var piece = pieces.get(i);
              check(piece.index() == i && piece.corners().size() == 8, "renderer index and collider corners match");
              check(piece.corners().stream().allMatch(v -> v.isFinite()), "finite geometry");
              check(piece.attachment() || piece.part() == NativeCouplerAnimation.Part.MOUNT, "beam cannot animate independently");
            }
            models++;
          }
        }
      }
    }
    var child = JsonParser.parseString("{\"parent\":\"test:base\",\"textures\":{\"metal\":\"railways:child\"}}").getAsJsonObject();
    var parent = JsonParser.parseString("{\"elements\":[],\"textures\":{\"metal\":\"railways:base\",\"other\":\"railways:other\"}}").getAsJsonObject();
    var before = parent.deepCopy();
    var resolved = NativeCouplerModel.resolve(child, (namespace, path) -> parent);
    check(resolved.getAsJsonObject("textures").get("metal").getAsString().equals("railways:child"), "child texture wins");
    check(resolved.getAsJsonObject("textures").has("other") && parent.equals(before), "inherit without mutating cached source");
    try {
      NativeCouplerModel.resolve(child, (namespace, path) -> child);
      throw new AssertionError("cyclic parents accepted");
    } catch (IOException expected) { checks++; }
    check(models == 25, "all supported mount/kind/flip combinations covered");
    System.out.println("NativeCouplerModelCheck: " + models + " release models, " + checks + " shared geometry and inheritance checks passed");
  }
}
