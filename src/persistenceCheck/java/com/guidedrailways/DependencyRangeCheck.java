package com.guidedrailways;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/** Exercise the loader's Maven version ordering, including NeoForge Railway beta qualifiers. */
public final class DependencyRangeCheck {
  public static void main(String[] args) throws Exception {
    var ids =
        Map.of(
            "sable",
            "sable",
            "bnb",
            "create_bb",
            "cee",
            "electroenergetics",
            "railways",
            "railways",
            "aero",
            "simulated",
            "create",
            "create");
    var policy =
        JsonParser.parseString(Files.readString(Path.of("verification/dependency-policy.json")))
            .getAsJsonObject()
            .getAsJsonObject("supported");
    String toml;
    try (var stream =
        DependencyRangeCheck.class.getResourceAsStream("/META-INF/neoforge.mods.toml")) {
      toml =
          new String(
              java.util.Objects.requireNonNull(stream).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
    }
    int checked = 0;
    for (var entry : ids.entrySet()) {
      var match =
          Pattern.compile(
                  "modId = \""
                      + entry.getValue()
                      + "\"\\s+type = \"(?:required|optional)\"\\s+versionRange = \"([^\"]+)\"")
              .matcher(toml);
      if (!match.find()) throw new AssertionError("Missing dependency: " + entry.getValue());
      var range = VersionRange.createFromVersionSpec(match.group(1));
      for (var value : policy.getAsJsonArray(entry.getKey())) {
        String version = value.getAsString();
        if (entry.getKey().equals("sable")
            || entry.getKey().equals("aero")
            || entry.getKey().equals("create")) version = version.replace("+mc1.21.1", "");
        if (entry.getKey().equals("bnb")) version = version.replace("-1.21.1", "");
        if (!range.containsVersion(new DefaultArtifactVersion(version)))
          throw new AssertionError(entry.getValue() + " " + version + " excluded by " + range);
        checked++;
      }
    }
    System.out.println(
        "DependencyRangeCheck: "
            + checked
            + " supported release versions admitted by loader ranges");
  }
}
