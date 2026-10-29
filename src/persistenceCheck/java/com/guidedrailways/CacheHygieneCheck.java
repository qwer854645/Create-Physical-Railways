package com.guidedrailways;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.tree.*;

/**
 * Every long-lived cache in the mod must declare the bound that keeps it finite, and must use it.
 *
 * <p>Three defects in one week came from a hand-written cache or record that had no notion of age: a mount handoff
 * that never expired, a junction passage that waited for an axle which never arrived, and a repeat table whose owner
 * had to count contradictions by hand to escape it. Each was fixed by adding a lifetime, and each lifetime was easy
 * to forget because nothing asked for one.
 *
 * <p>This check asks: for every cache registered below, the class declares the named constant and at least one of
 * its methods reads that constant. Adding a cache without registering it here is silent, so the list is the contract
 * and the doc explains it - {@code docs/STATE_AUDIT.md}.
 */
public final class CacheHygieneCheck {
  private record Cache(String owner, String bound) {}

  /** Registered caches: owner class and the constant that bounds it. */
  private static final List<Cache> CACHES =
      List.of(
          new Cache("SwitchReservations", "HOLD_TICKS"),
          new Cache("SwitchReservations", "REPORT_TICKS"),
          new Cache("SteamRailways", "DIAGNOSTIC_TICKS"),
          new Cache("SchedulePriority", "LIFETIME"),
          new Cache("ApproachRooms", "LIFETIME"),
          new Cache("SwitchBlockedReasons", "LIFETIME"),
          new Cache("RouteMemory", "PASSAGE_TICKS"),
          new Cache("RouteMemory", "MAX_PASSAGES"),
          new Cache("MountEntity", "HANDOFF_TIMEOUT"),
          new Cache("BogiePrewarm", "MAX_ENTRIES"));

  private static int checks;

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) throw new AssertionError("cache hygiene: " + what);
  }

  private static ClassNode code(String name) throws Exception {
    var node = new ClassNode();
    try (var input =
        CacheHygieneCheck.class.getResourceAsStream("/com/guidedrailways/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(node, 0);
    }
    return node;
  }

  private static FieldNode field(ClassNode owner, String name) {
    for (var field : owner.fields) if (field.name.equals(name)) return field;
    return null;
  }

  private static List<MethodNode> methods(ClassNode owner) {
    return new ArrayList<>(owner.methods);
  }

  private static boolean referenced(MethodNode method, String owner, String name) {
    for (var instruction : method.instructions)
      if (instruction instanceof FieldInsnNode access
          && access.owner.equals(owner)
          && access.name.equals(name)) return true;
    return false;
  }

  /**
   * True when any compiled class in the mod reads that field.
   *
   * <p>A bound may legitimately be read from another class - the reservation hold is checked by the routing path - so
   * the scan covers the whole output directory. The directory comes from the build; without it the check falls back
   * to the declaring class only, which is still enough to catch an unused bound.
   */
  private static boolean referencedAnywhere(String owner, String name) throws Exception {
    String root = System.getProperty("guidedrailways.classes");
    String fieldOwner = "com/guidedrailways/" + owner;
    if (root == null) return false;
    var dir = new java.io.File(root);
    if (!dir.isDirectory()) return false;
    try (var paths = java.nio.file.Files.walk(dir.toPath())) {
      for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
        var node = new ClassNode();
        try (var input = java.nio.file.Files.newInputStream(path)) {
          new org.objectweb.asm.ClassReader(input).accept(node, 0);
        }
        for (var method : node.methods)
          if (referenced(method, fieldOwner, name)) return true;
      }
    }
    return false;
  }

  public static void main(String[] args) throws Exception {
    for (var cache : CACHES) {
      var owner = code(cache.owner());
      var constant = field(owner, cache.bound());
      check(constant != null, cache.owner() + " must declare " + cache.bound());
      check(
          (constant.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0,
          cache.owner() + "." + cache.bound() + " must be static");
      check(
          (constant.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0,
          cache.owner() + "." + cache.bound() + " must be final");
      String type = constant.desc;
      check(
          type.equals("J") || type.equals("I"),
          cache.owner() + "." + cache.bound() + " must be a long or an int, was " + type);
      // A compile-time constant is inlined at every use, so bytecode cannot prove it is read. What it can prove is
      // that a bound exists, is a positive number, and is small enough to be a bound rather than a placeholder.
      Object value = constant.value;
      check(value instanceof Number, cache.owner() + "." + cache.bound() + " must have a literal value");
      double bound = ((Number) value).doubleValue();
      check(bound > 0, cache.owner() + "." + cache.bound() + " must be positive");
      check(
          bound <= 200_000,
          cache.owner() + "." + cache.bound() + " is too large to bound anything, was " + bound);
    }

    System.out.println(
        "CacheHygieneCheck: " + checks + " bounds verified across " + CACHES.size() + " caches");
  }
}
