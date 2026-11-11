package com.physicalrailways;

import java.lang.reflect.*;
import java.util.*;

/** Exercises the real transient incident registry without bootstrapping a Minecraft world. */
public final class FaultRecoveryCheck {
  private static int checks;

  private static void check(boolean value, String why) {
    checks++;
    if (!value) throw new AssertionError(why);
  }

  private static Object create(String name) throws Exception {
    var constructor = Class.forName("com.physicalrailways.TrainFaults$" + name).getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  private static Field field(Object target, String name) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static RecoveryWindow window(Object incident) throws Exception {
    return (RecoveryWindow) field(incident, "window").get(incident);
  }

  private static TrainConsists.Group group(UUID... members) {
    return new TrainConsists.Group(Set.of(members), List.of(), Map.of(), List.of());
  }

  public static void main(String[] args) throws Exception {
    var world = create("World");
    var original = create("Incident");
    field(original, "fault").setBoolean(original, true);
    field(original, "nextLog").setLong(original, 200);
    var retarget = TrainFaults.class.getDeclaredMethod("retarget", world.getClass(),
        TrainConsists.Group.class, original.getClass(), long.class);
    retarget.setAccessible(true);
    @SuppressWarnings("unchecked")
    var incidents = (Map<UUID, Object>) field(world, "incidents").get(world);
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var other = UUID.randomUUID();
    retarget.invoke(null, world, group(a, b), original, 0L);
    var coupled = incidents.get(a);
    check(coupled == incidents.get(b), "one consist shares one safety hold");
    check(!incidents.containsKey(other), "unrelated trains acquire no incident");
    check(field(coupled, "fault").getBoolean(coupled), "retarget retains the runtime fault reason");
    check(field(coupled, "nextLog").getLong(coupled) == 200, "topology changes cannot bypass log throttling");
    retarget.invoke(null, world, group(a), coupled, 5L);
    retarget.invoke(null, world, group(b), coupled, 5L);
    var left = incidents.get(a);
    var right = incidents.get(b);
    check(left != right, "intentional uncoupling gives each train an independent readiness clock");
    check(field(left, "expected").get(left).equals(Set.of(a)), "left train accepts only its new membership");
    check(field(right, "expected").get(right).equals(Set.of(b)), "right train accepts only its new membership");
    for (int tick = 25; tick <= 34; tick++) window(left).healthy(tick);
    check(!window(left).blocked() && window(right).blocked(), "one recovered half cannot release the unready half");
    check(window(coupled).blocked(), "retargeting cannot mutate an old group's recovery state");
    retarget.invoke(null, world, group(a, b), right, 40L);
    var merged = incidents.get(a);
    check(merged == incidents.get(b) && merged != left && merged != right, "recoupling installs one fresh shared hold");
    check(field(merged, "expected").get(merged).equals(Set.of(a, b)), "recoupled readiness requires the complete consist");
    check(!window(merged).probe(59) && window(merged).probe(60), "recoupling restarts readiness rather than inheriting partial stability");
    for (int tick = 60; tick <= 69; tick++) window(merged).healthy(tick);
    check(!window(merged).blocked(), "complete recoupled consist recovers after a fresh stable window");
    System.out.println("FaultRecoveryCheck: " + checks + " production incident alias, split and recoupling checks passed");
  }
}
