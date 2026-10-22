package com.guidedrailways;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Real saved pairing and NBT, including a copy loaded before its original endpoint. */
public final class CouplerOwnershipCheck {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    var data = new CouplerSavedData();
    var link = new CouplingLinks.Link(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var a = new BlockPos(100, 64, 200);
    var b = new BlockPos(120, 64, 200);
    var copy = new BlockPos(300, 64, 200);
    data.add(link, a, b);
    var saved = data.save(new CompoundTag(), null);
    data = CouplerSavedData.load(saved, null);
    check(!data.claim(link.a(), copy), "copy cannot claim saved identity before original loads");
    check(!data.owns(link.a(), copy), "copy destruction cannot authorize pair removal");
    check(data.claim(link.a(), a), "original restores its identity after the copy was rejected");
    check(
        link.equals(data.links.forEndpoint(link.a())), "rejected copy leaves original pair intact");
    check(!data.move(link.a(), copy, b), "a copied source cannot authorize a handoff");
    check(data.move(link.a(), a, copy), "normal assembly handoff updates the location");
    check(data.move(link.a(), a, copy), "retry after ownership commit is idempotent");
    check(!data.owns(link.a(), a), "late source destruction cannot unlink the moved endpoint");
    check(data.claim(link.a(), copy), "destination can register before source cleanup");
    data = CouplerSavedData.load(data.save(new CompoundTag(), null), null);
    check(data.move(link.a(), a, copy), "a saved committed handoff accepts the same destination");
    check(data.owns(link.a(), copy) && !data.owns(link.a(), a), "moved ownership survives reload");
    check(data.move(link.a(), copy, a), "reverse handoff preserves identity");
    data.remove(link.id());
    check(
        data.links.forEndpoint(link.a()) == null && data.links.forEndpoint(link.b()) == null,
        "unlink removes both pair aliases");
    check(
        data.owns(link.a(), copy) && data.owns(link.b(), copy),
        "unlink releases both owner records");
    var legacy = new CompoundTag();
    var entries = new net.minecraft.nbt.ListTag();
    var pair = new CompoundTag();
    pair.putUUID("Id", link.id());
    pair.putUUID("A", link.a());
    pair.putUUID("B", link.b());
    entries.add(pair);
    legacy.put("Links", entries);
    data = CouplerSavedData.load(legacy, null);
    check(
        data.claim(link.a(), a) && data.isDirty(),
        "unregistered older pairs acquire an owner once");
    check(!data.claim(link.a(), copy), "older pair becomes protected after adoption");
    System.out.println(
        "CouplerOwnershipCheck: " + checks + " saved ownership and handoff checks passed");
  }
}
