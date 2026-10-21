# Discovery callback startup conflict

Report: `错误报告-2026-10-21_7.34.39.zip`, supplied 2026-10-21.

## Evidence

- Physical Railways 0.2.12, Create 6.0.10, Railway 0.3.0-beta.2,
  Sable 2.0.6, Aeronautics Discovery 2.2.0, NeoForge 21.1.255.
- `debug.log` at 07:33:59.901 records Discovery's
  `hook.BlockWithSubLevelCallbackMixin` applied to
  `BlockWithSubLevelCollisionCallback`. Its injection is named
  `aeronauticsdiscovery$wrapCallback`.
- At 07:34:05.946, our `MonorailTrackContactMixin` is applied successfully
  to Railway's `MonorailTrackBlock`.
- At 07:34:25.932, Mixin generates Discovery's `ChainedCallback` class.
- At 07:34:25.933, our verifier throws `Monorail contact callback missing`.
  It compared the public lookup result by identity with `MonorailContacts.INSTANCE`.
  A wrapping chain has a different identity even when the block registration is valid.
- The later Sodium Extra configuration exception appears after this loading failure;
  the report does not establish an independent Sodium Extra fault.

## Correction

Verify that the monorail block implements Sable's callback interface and its direct
getter returns our registered callback. Also require the public lookup to return a
non-null callback, allowing wrappers. Continue throwing when either requirement
fails. Do not bypass Discovery's callback chain during physics, change contact
filtering, or remove the registration check.

## Validation boundaries

The offline build and project checks validate compilation and existing checks.
The reporter's exact modpack and Discovery 2.2.0 are not installed in the development
runtime. No client or server was launched for this investigation. Acceptance still
requires starting with Discovery 2.2.0 and checking guided monorail movement alongside
ordinary block contacts; startup without Discovery must also remain valid.
