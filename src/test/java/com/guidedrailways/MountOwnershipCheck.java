package com.guidedrailways;

public final class MountOwnershipCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static MountOwnership.Result apply(MountOwnership owner, MountOwnership.Location to) {
    return owner.moveTo(owner.location(), to, owner.revision()).result();
  }

  public static void main(String[] args) {
    var a = new MountOwnership.Location("minecraft:overworld", 100L);
    var b = new MountOwnership.Location("minecraft:overworld", 200L);
    var c = new MountOwnership.Location("minecraft:overworld", 300L);
    var nether = new MountOwnership.Location("minecraft:the_nether", 100L);

    var claim = MountOwnership.firstClaim(a);
    check(claim.revision() == 0, "a first claim starts at revision 0");
    check(claim.matches(a), "a claim matches its own location");
    check(!claim.matches(b), "a claim does not match another location");
    check(!claim.matches(nether), "the same block in another dimension is another location");

    var forward = claim.moveTo(a, b, 0);
    check(
        forward.result() == MountOwnership.Result.APPLIED && forward.changed(),
        "observed source revision authorizes the move");
    check(forward.ownership().matches(b), "the applied claim records the destination");
    check(forward.ownership().revision() == 1, "an applied move advances the revision");
    check(claim.matches(a) && claim.revision() == 0, "the source claim is not mutated");

    var home = forward.ownership().moveTo(b, a, 1);
    check(home.result() == MountOwnership.Result.APPLIED, "the return move is authorized");
    check(
        home.ownership().matches(a) && home.ownership().revision() == 2,
        "returning to the first location keeps a distinct revision instead of looking original");

    var stale = home.ownership().moveTo(a, c, 0);
    check(
        stale.result() == MountOwnership.Result.REJECTED_STALE,
        "a handoff authorized before the return move is stale");
    check(
        stale.ownership() == home.ownership() && stale.ownership().matches(a),
        "a stale handoff leaves the current claim untouched");
    check(!stale.changed(), "a stale handoff reports no change");

    var unobserved = claim.moveTo(a, b, MountOwnership.UNKNOWN_REVISION);
    check(
        unobserved.result() == MountOwnership.Result.APPLIED,
        "an unobservable source keeps the legacy location-only behaviour");

    var wrongSource = forward.ownership().moveTo(a, c, 1);
    check(
        wrongSource.result() == MountOwnership.Result.REJECTED_SOURCE,
        "only the recorded source may authorize a move");
    check(wrongSource.ownership().matches(b), "a rejected source leaves the claim untouched");

    var replay = forward.ownership().moveTo(a, b, 0);
    check(
        replay.result() == MountOwnership.Result.REPLAYED,
        "replaying the committed move is idempotent");
    check(
        replay.ownership().revision() == 1 && !replay.changed(),
        "a replayed callback does not advance the revision again");

    var reversedReplay = home.ownership().moveTo(c, a, 0);
    check(
        reversedReplay.result() == MountOwnership.Result.REPLAYED,
        "a late callback targeting the current location is idempotent even with an old revision");
    check(reversedReplay.ownership().revision() == 2, "the duplicate leaves revision 2 in place");

    var last = new MountOwnership(c, Long.MAX_VALUE);
    var overflow = apply(last, a);
    check(
        overflow == MountOwnership.Result.REJECTED_OVERFLOW,
        "a revision that cannot advance is rejected before writing");
    check(last.matches(c) && last.revision() == Long.MAX_VALUE, "overflow leaves the claim intact");

    boolean rejectedNegative;
    try {
      new MountOwnership(a, -1);
      rejectedNegative = false;
    } catch (IllegalArgumentException expected) {
      rejectedNegative = true;
    }
    check(rejectedNegative, "a negative revision cannot exist");

    boolean rejectedNullLocation;
    try {
      new MountOwnership(null, 0);
      rejectedNullLocation = false;
    } catch (NullPointerException expected) {
      rejectedNullLocation = true;
    }
    check(rejectedNullLocation, "a claim always has a location");

    // A copied plate carries the same block coordinates as the original; only one claim may match.
    var other = MountOwnership.firstClaim(b);
    check(
        !claim.matches(b) && other.matches(b),
        "two plates at different locations cannot both own the same body");

    System.out.println("MountOwnershipCheck: " + checks + " assertions passed");
  }
}
