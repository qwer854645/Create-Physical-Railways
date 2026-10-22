package com.guidedrailways;

import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.objectweb.asm.tree.*;

/** Replay connected moves using production endpoint NBT, saved pairing and handoff transitions. */
public final class CouplerLifecycleCheck {
  private static int checks;
  private static final String WORLD = "minecraft:overworld";

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static CompoundTag disk(CompoundTag tag) throws IOException {
    var bytes = new ByteArrayOutputStream();
    NbtIo.write(tag, new DataOutputStream(bytes));
    return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  }

  private static CouplerEndpoint reload(CouplerEndpoint endpoint) throws IOException {
    var restored = new CouplerEndpoint(null);
    restored.read(disk(endpoint.save()));
    return restored;
  }

  private static ClassNode code(String name) throws IOException {
    var code = new ClassNode();
    try (var input = CouplerLifecycleCheck.class.getResourceAsStream("/" + name + ".class")) {
      new org.objectweb.asm.ClassReader(Objects.requireNonNull(input, name)).accept(code, 0);
    }
    return code;
  }

  private static void boundaries() throws IOException {
    var upstream = code("dev/ryanhcode/sable/api/SubLevelAssemblyHelper");
    var move =
        upstream.methods.stream()
            .filter(m -> m.name.equals("moveBlocks"))
            .findFirst()
            .orElseThrow();
    int before = -1, save = -1, load = -1, after = -1;
    for (var instruction : move.instructions)
      if (instruction instanceof MethodInsnNode call) {
        int index = move.instructions.indexOf(call);
        if (call.name.equals("beforeMove")) before = index;
        if (call.name.equals("saveWithFullMetadata")) save = index;
        if (call.name.equals("loadWithComponents")) load = index;
        if (call.name.equals("afterMove")) after = index;
      }
    check(
        before >= 0 && before < save && save < load && load < after,
        "Sable must capture after beforeMove and load before afterMove");
    check(
        code("com/guidedrailways/CouplerTransfers").fields.isEmpty(),
        "assembly adapter cannot retain a second identity or timeout map");
    for (var method : code("com/guidedrailways/CouplerConnections").methods)
      if (method.name.equals("unload") || method.name.equals("suspend"))
        for (var instruction : method.instructions)
          if (instruction instanceof MethodInsnNode call)
            check(
                !(call.owner.equals("com/guidedrailways/CouplerSavedData")
                        && call.name.equals("remove"))
                    && !(call.owner.equals("com/guidedrailways/CouplerConnections")
                        && call.name.equals("disconnect")),
                "unload/suspension cannot delete saved pairs");
  }

  public static void main(String[] args) throws Exception {
    boundaries();
    var a = new BlockPos(100, 64, 200);
    var b = new BlockPos(200, 64, 200);
    var c = new BlockPos(300, 64, 200);
    var foreign = new BlockPos(400, 64, 200);
    var peer = new BlockPos(110, 64, 200);
    var endpoint = new CouplerEndpoint(null);
    endpoint.hasGeometry = true;
    endpoint.restLength = 1.125;
    endpoint.visualConnected = true;
    endpoint.visualPeerId = UUID.randomUUID();
    endpoint.visualPeerPos = peer;
    var pair =
        new CouplingLinks.Link(UUID.randomUUID(), endpoint.endpointId, endpoint.visualPeerId);
    var data = new CouplerSavedData();
    data.add(pair, a, peer);
    var anchor = new Vector3d(4, 2, 8);
    var frame = new Quaterniond().rotationY(.8);
    endpoint.handoff =
        CouplerHandoff.begin(
            null,
            endpoint.endpointId,
            WORLD,
            a.asLong(),
            b.asLong(),
            anchor,
            frame,
            endpoint.restLength);
    endpoint = reload(endpoint);
    data = CouplerSavedData.load(disk(data.save(new CompoundTag(), null)), null);
    var request = endpoint.handoff;
    check(
        request != null
            && request.worldAnchor().equals(anchor, 1e-12)
            && request.worldFrame().equals(frame, 1e-12),
        "world geometry survives endpoint and pair reload");
    check(
        request.protectsSource(endpoint.endpointId, WORLD, a.asLong()),
        "assembly source removal preserves the saved pair");
    check(
        request.commit(data, endpoint.endpointId, WORLD, b, false) == CouplerHandoff.Result.WAITING,
        "mass/body readiness delays restoration without moving saved ownership");
    check(
        data.owns(endpoint.endpointId, a)
            && pair.equals(data.links.forEndpoint(endpoint.endpointId)),
        "waiting keeps source ownership and actual pairing");
    for (int i = 0; i < 5; i++) {
      endpoint = reload(endpoint); // No timer can expire the saved request between retries.
      check(
          endpoint.handoff.commit(data, endpoint.endpointId, WORLD, b, false)
              == CouplerHandoff.Result.WAITING,
          "repeated unload/reload retains pending restoration");
    }
    var copy = reload(endpoint);
    check(
        copy.handoff.commit(data, copy.endpointId, WORLD, foreign, true)
            == CouplerHandoff.Result.REJECTED,
        "copy at another position cannot consume a move request");
    check(
        !copy.handoff.authorizes(data, copy.endpointId, WORLD, foreign)
            && !data.claim(copy.endpointId, foreign),
        "copy cannot authorize destruction or claim original pair");
    check(
        request.commit(data, endpoint.endpointId, "minecraft:the_nether", b, true)
            == CouplerHandoff.Result.REJECTED,
        "copied cross-dimension request is rejected");
    // Move B to C before B can build geometry: source must still be A.
    endpoint.handoff =
        CouplerHandoff.begin(
            endpoint.handoff,
            endpoint.endpointId,
            WORLD,
            b.asLong(),
            c.asLong(),
            new Vector3d(99),
            new Quaterniond(),
            99);
    endpoint = reload(endpoint);
    check(
        endpoint.handoff.source() == a.asLong()
            && endpoint.handoff.worldAnchor().equals(anchor, 1e-12),
        "consecutive pending moves preserve original source and geometry");
    check(
        endpoint.handoff.commit(data, endpoint.endpointId, WORLD, c, true)
            == CouplerHandoff.Result.READY,
        "final ready destination commits directly from original source");
    endpoint.handoff = endpoint.handoff.ownershipCommitted();
    endpoint = reload(endpoint);
    data = CouplerSavedData.load(disk(data.save(new CompoundTag(), null)), null);
    check(
        endpoint.handoff.source() == c.asLong() && data.owns(endpoint.endpointId, c),
        "partial committed state survives save before geometry completion");
    endpoint.handoff =
        CouplerHandoff.begin(
            endpoint.handoff, endpoint.endpointId, WORLD, c.asLong(), a.asLong(), null, null, -1);
    check(
        endpoint.handoff.commit(data, endpoint.endpointId, WORLD, a, true)
            == CouplerHandoff.Result.READY,
        "reverse move uses committed source rather than an obsolete position");
    check(
        data.owns(endpoint.endpointId, a) && !data.owns(endpoint.endpointId, c),
        "late source cannot reclaim the final owner");
    endpoint.handoff = null;
    endpoint = reload(endpoint);
    check(endpoint.handoff == null, "finished endpoint no longer carries a pending request");
    // Destruction at a not-yet-ready legitimate destination must remove the pair, not orphan it.
    request =
        CouplerHandoff.begin(
            null, endpoint.endpointId, WORLD, a.asLong(), b.asLong(), anchor, frame, 1.125);
    check(
        request.authorizes(data, endpoint.endpointId, WORLD, b),
        "legitimate pending destination can authorize destruction even before body readiness");
    data.remove(pair.id());
    check(
        data.links.forEndpoint(pair.a()) == null && data.links.forEndpoint(pair.b()) == null,
        "pending destination removal clears both sides");
    var malformed = request.save();
    malformed.getCompound("Geometry").putDouble("X", Double.NaN);
    check(
        CouplerHandoff.load(disk(malformed)) == null,
        "invalid geometry cannot authorize restoration");
    System.out.println(
        "CouplerLifecycleCheck: "
            + checks
            + " actual NBT, pairing, consecutive move and callback-boundary checks passed; no world started");
  }
}
