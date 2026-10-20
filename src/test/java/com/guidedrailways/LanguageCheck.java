package com.guidedrailways;

import com.google.gson.stream.JsonReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Resource and player-message coverage checks; no Minecraft runtime or server. */
public final class LanguageCheck {
  private static int checks;

  private static void check(boolean ok, String why) {
    checks++;
    if (!ok) throw new AssertionError(why);
  }

  private static Map<String, String> language(String locale) throws IOException {
    var result = new LinkedHashMap<String, String>();
    try (var reader =
        new JsonReader(
            Files.newBufferedReader(
                Path.of("src/main/resources/assets/guidedrailways/lang/" + locale + ".json"),
                StandardCharsets.UTF_8))) {
      reader.beginObject();
      while (reader.hasNext()) {
        String key = reader.nextName(), value = reader.nextString();
        check(result.putIfAbsent(key, value) == null, "duplicate translation: " + key);
        check(!value.isBlank(), "empty translation: " + key);
        check(!value.contains("\\n"), "literal newline escape would display in tooltip: " + key);
        check(
            !value.endsWith("。") && !value.endsWith("."),
            "short UI messages use no trailing full stop: " + key);
      }
      reader.endObject();
    }
    return result;
  }

  private static List<String> placeholders(String value) {
    var result = new ArrayList<String>();
    var matcher = Pattern.compile("%%|%(?:[0-9]+\\$)?[sd]").matcher(value);
    while (matcher.find()) result.add(matcher.group());
    return result;
  }

  public static void main(String[] args) throws IOException {
    var zh = language("zh_cn");
    var en = language("en_us");
    check(zh.keySet().equals(en.keySet()), "Chinese and English cover identical keys");
    for (String key : zh.keySet())
      check(
          placeholders(zh.get(key)).equals(placeholders(en.get(key))),
          "format argument mismatch: " + key);
    var literal =
        Pattern.compile(
            "\"((?:itemGroup|item|block|tooltip|gui|message|config)\\.guidedrailways\\.[A-Za-z0-9_.]+)\"");
    var helper = Pattern.compile("text\\(\"([a-z_]+)\"");
    var hint = Pattern.compile("hint\\(player,\"([a-z_]+)\"");
    try (var paths = Files.walk(Path.of("src/main/java/com/guidedrailways"))) {
      for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path);
        var matcher = literal.matcher(source);
        while (matcher.find()) {
          String key = matcher.group(1);
          if (!key.endsWith("."))
            check(en.containsKey(key), "missing literal key in " + path + ": " + key);
        }
        matcher = helper.matcher(source);
        while (matcher.find())
          check(
              en.containsKey("gui.guidedrailways.driver." + matcher.group(1)),
              "missing driver helper key: " + matcher.group(1));
        matcher = hint.matcher(source);
        while (matcher.find()) {
          String prefix =
              path.getFileName().toString().equals("ScheduleController.java")
                  ? "gui.guidedrailways.auto."
                  : path.getFileName().toString().equals("RailDockInteractions.java")
                      ? "message.guidedrailways.rail_dock."
                      : "gui.guidedrailways.driver.";
          check(
              en.containsKey(prefix + matcher.group(1)),
              "missing action hint: " + prefix + matcher.group(1));
        }
        check(
            !Pattern.compile("[\\x{4e00}-\\x{9fff}]").matcher(source).find(),
            "hard-coded Chinese bypasses localization: " + path);
        if (!path.getFileName().toString().equals("UserText.java"))
          check(
              !source.contains("Component.literal("), "unlocalized player-facing literal: " + path);
      }
    }
    for (int state = 0; state <= 21; state++)
      check(
          en.containsKey("gui.guidedrailways.auto.state." + state),
          "missing automatic state " + state);
    for (String key :
        List.of(
            "steer_left",
            "steer_right",
            "steer_default",
            "brake_notch",
            "coasting",
            "world_hint",
            "dragging_hint",
            "reset_hint",
            "observing",
            "emergency_on",
            "back_settings",
            "gear_settings"))
      check(
          en.containsKey("gui.guidedrailways.driver." + key),
          "missing conditional driver text: " + key);
    for (String key : List.of("bogie", "coupler", "driver_console"))
      check(
          zh.get("tooltip.guidedrailways." + key).contains("\n"),
          "item tooltip has actual translated line breaks");
    System.out.println(
        "LanguageCheck: " + checks + " assertions passed; " + en.size() + " keys per language");
  }
}
