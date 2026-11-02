package com.guidedrailways;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;
import jdk.jfr.*;
import org.objectweb.asm.tree.ClassNode;

/** Opt-in diagnosis only. Client mixins are not applied at all during ordinary launches. */
public final class LagProbe {
  public enum Stage { WORLD_REBUILD, CHUNK_UPLOAD_BATCH, VBO_BIND, VBO_UPLOAD, VBO_CLOSE, SABLE_RENDER_RELEASE, PHYSICS_PROPERTY_SYNC, BOGIE_REMOVE }
  private static final boolean ENABLED = Boolean.getBoolean("guidedrailways.lagProbe");
  private static final AtomicLongArray LAST_LOG = new AtomicLongArray(Stage.values().length);
  public static boolean enabled() { return ENABLED; }
  public static long begin() { return ENABLED ? System.nanoTime() : 0; }

  public static void end(Stage stage, long start) {
    if (start == 0) return;
    long now = System.nanoTime(), elapsed = now - start;
    if (elapsed < 5_000_000) return;
    var event = new Span();
    if (event.isEnabled()) { event.stage = stage.name(); event.elapsedNanos = elapsed; event.commit(); }
    if (elapsed < 40_000_000) return;
    long previous = LAST_LOG.get(stage.ordinal());
    if (previous != 0 && now >= previous && now - previous < 1_000_000_000) return;
    if (LAST_LOG.compareAndSet(stage.ordinal(), previous, now))
      org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[LagProbe] stage={} ms={} thread={}",
          stage, elapsed / 1e6, Thread.currentThread().getName());
  }

  @Name("guidedrailways.LagSpan") @Label("Guided Railways slow operation") @Category("Guided Railways") @StackTrace(true)
  static final class Span extends Event {
    @Label("Operation") public String stage;
    @Label("Elapsed time") @Timespan(Timespan.NANOSECONDS) public long elapsedNanos;
  }

  /** Unsupported versions skip probes, without marking gameplay compatibility hooks unavailable. */
  public static boolean supports(String name, ClassNode node) {
    Set<String> required = switch (name) {
      case "LagProbeWorldMixin" -> Set.of("allChanged()V");
      case "LagProbeUploadsMixin" -> Set.of("uploadAllPendingUploads()V");
      case "LagProbeVertexMixin" -> Set.of("bind()V", "close()V", "upload(Lcom/mojang/blaze3d/vertex/MeshData;)V");
      case "LagProbeSableReleaseMixin" -> Set.of("close()V");
      case "LagProbePropertiesMixin" -> Set.of("lambda$handle$0()V");
      default -> Set.of();
    };
    if (required.isEmpty()) return false;
    var methods = new java.util.HashSet<String>();
    for (var method : node.methods) {
      String signature = method.name + method.desc;
      if (required.contains(signature) && (method.access & (org.objectweb.asm.Opcodes.ACC_STATIC | org.objectweb.asm.Opcodes.ACC_ABSTRACT)) != 0) return false;
      methods.add(signature);
    }
    return methods.containsAll(required);
  }
  private LagProbe() {}
}
