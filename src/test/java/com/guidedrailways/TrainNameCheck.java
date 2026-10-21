package com.guidedrailways;

public final class TrainNameCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(TrainName.normalize(null).isEmpty(), "absent name uses default");
    check(TrainName.normalize(" \t\n\u3000").isEmpty(), "blank name clears override");
    check(TrainName.normalize("  A  B\nC  ").equals("A B C"), "single-line trimmed spacing");
    String unicode = "\u7269\u7406\u94c1\u8def \ud83d\ude86";
    check(TrainName.normalize(unicode).equals(unicode), "Unicode train names preserved");
    check(
        TrainName.normalize("A\u00a7B\u0000C\u007f").equals("ABC"),
        "control and formatting introducers removed");
    check(
        TrainName.normalize("x".repeat(100)).length() == TrainName.MAX_LENGTH,
        "bounded name length");
    String emoji = "\ud83d\ude86";
    check(
        TrainName.normalize("x".repeat(63) + emoji).equals("x".repeat(63)),
        "truncation never splits surrogate pair");
    check(
        TrainName.normalize(emoji.repeat(50)).equals(emoji.repeat(32)),
        "supplementary characters count toward wire limit");
    check(TrainName.normalize("A\ud800B\udc00C").equals("ABC"), "unpaired surrogates removed");
    for (var input :
        new String[] {
          "", "North Express", unicode, "a".repeat(200), "  A  B  ", emoji.repeat(40)
        }) {
      var name = TrainName.normalize(input);
      check(TrainName.normalize(name).equals(name), "save/load normalization is stable");
      check(name.length() <= TrainName.MAX_LENGTH, "always fits network and text field");
    }
    System.out.println("TrainNameCheck: " + checks + " Unicode, clearing and length checks passed");
  }
}
