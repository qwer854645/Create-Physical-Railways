package com.guidedrailways;

import com.guidedrailways.mixin.ShaderSamplerLocationMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.*;
import org.objectweb.asm.tree.*;

/** Invoke the actual wrapper with a counting driver stub; never creates an OpenGL context. */
public final class ShaderSamplerReuseCheck {
  private static int queries;
  private static final Operation<Integer> DRIVER = args -> { queries++; return 99; };

  private static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  private static void field(Object target, String name, Object value) throws Exception {
    var f = ShaderSamplerLocationMixin.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static int lookup(Object target, int program, CharSequence name) throws Exception {
    var method = ShaderSamplerLocationMixin.class.getDeclaredMethod(
        "guidedrailways$samplerLocation", int.class, CharSequence.class, Operation.class);
    method.setAccessible(true);
    return (int) method.invoke(target, program, name, DRIVER);
  }

  private static ClassNode code(String path) throws Exception {
    var node = new ClassNode();
    try (var input = ShaderSamplerReuseCheck.class.getResourceAsStream("/" + path + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node, 0);
    }
    return node;
  }

  public static void main(String[] args) throws Exception {
    boolean disabled = Boolean.getBoolean("guidedrailways.disableSamplerLocationReuse");
    var shader = new ShaderSamplerLocationMixin() {};
    var names = new ArrayList<>(List.of("Sampler0", "Sampler2"));
    var locations = new ArrayList<>(List.of(7, 23));
    field(shader, "programId", 42);
    field(shader, "samplerNames", names);
    field(shader, "samplerLocations", locations);
    for (int i = 0; i < 10000; i++)
      check(lookup(shader, 42, "Sampler2") == (disabled ? 99 : 23), "repeat draws preserve sampler slot");
    check(queries == (disabled ? 10000 : 0), "normal draws issue no driver queries when enabled");
    check(lookup(shader, 42, new StringBuilder("Sampler0")) == (disabled ? 99 : 7), "CharSequence supported");
    locations.set(1, 81);
    check(lookup(shader, 42, "Sampler2") == (disabled ? 99 : 81), "read updated native locations, not stale copies");
    locations.set(0, -1);
    check(lookup(shader, 42, "Sampler0") == (disabled ? 99 : -1), "inactive location is preserved");
    check(lookup(shader, 43, "Sampler2") == 99, "foreign program delegates");
    check(lookup(shader, 42, "ModSampler") == 99, "unknown sampler delegates");
    locations.removeLast();
    check(lookup(shader, 42, "Sampler0") == 99, "incomplete native lists delegate");
    field(shader, "samplerLocations", null);
    check(lookup(shader, 42, "Sampler0") == 99, "uninitialized state delegates");
    var reloaded = new ShaderSamplerLocationMixin() {};
    field(reloaded, "programId", 42);
    field(reloaded, "samplerNames", List.of("Sampler0"));
    field(reloaded, "samplerLocations", List.of(108));
    check(lookup(reloaded, 42, "Sampler0") == (disabled ? 99 : 108), "program ID reuse does not share shader cache");
    var nativeShader = code("net/minecraft/client/renderer/ShaderInstance");
    int targetCalls = 0;
    for (var m : nativeShader.methods) if (m.name.equals("apply"))
      for (var insn : m.instructions)
        if (insn instanceof MethodInsnNode call
            && call.owner.equals("com/mojang/blaze3d/shaders/Uniform")
            && call.name.equals("glGetUniformLocation")
            && call.desc.equals("(ILjava/lang/CharSequence;)I")) targetCalls++;
    check(targetCalls == 1, "installed Minecraft has exactly one apply sampler query target");
    var json = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/guidedrailways.mixins.json"));
    var config = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    check(config.getAsJsonArray("client").toString().contains("ShaderSamplerLocationMixin"), "client mixin registered");
    check(!config.getAsJsonArray("mixins").toString().contains("ShaderSamplerLocationMixin"), "server mixin list unchanged");
    System.out.println("ShaderSamplerReuseCheck: repeated draw, reload, ID reuse, fallback and native target passed; disabled=" + disabled);
  }
}
