package com.guidedrailways;

/** Plain single-line names. The limit also bounds UTF-16 network and edit-box lengths. */
public final class TrainName {
  public static final int MAX_LENGTH = 64;

  public static String normalize(String input) {
    if (input == null) return "";
    var result = new StringBuilder();
    for (int offset = 0; offset < input.length(); ) {
      int cp = input.codePointAt(offset);
      offset += Character.charCount(cp);
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) cp = ' ';
      if (Character.isISOControl(cp) || cp == 0x00a7 || cp >= 0xd800 && cp <= 0xdfff) continue;
      if (cp == ' ' && (result.isEmpty() || result.charAt(result.length() - 1) == ' ')) continue;
      if (result.length() + Character.charCount(cp) > MAX_LENGTH) break;
      result.appendCodePoint(cp);
    }
    return result.toString().strip();
  }

  private TrainName() {}
}
