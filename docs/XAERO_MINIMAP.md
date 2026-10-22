# Xaero's Minimap markers

This optional client adapter adds a locomotive-shaped marker and train name to
Xaero's Minimap. It uses the native overlay element API, checked against installed
Xaero's Minimap 26.6.0 for Minecraft 1.21.1. It is not a universal map-mod or world-map
adapter. No Xaero classes are required when the mod is absent.

## Data and rendering

- Only `PhysicalTrainProxy` identities already published by the existing bridge
  appear. A consist with a valid primary console produces one marker per UUID;
  bogeys, secondary consoles and carriages do not create separate markers.
- Loaded carriage positions come from Sable's live body bounds. If a body is
  unloaded, the existing native query snapshot provides its position. The marker
  is the mean of carriage centers; no additional position packets are sent.
- Markers are refreshed on client ticks and interpolated for rendering. Large
  position discontinuities snap instead of sweeping across the map.
- Xaero handles minimap rotation, zoom and boundary projection. Markers projected
  outside the map are skipped. Gold marks a normal physical train, red marks the
  bridge's derailed state, and names are shortened to fit the label width.
- Quads and text use Xaero's supplied render buffers; no per-marker buffer flush,
  synthetic entity or persistent waypoint is created.
- The adapter registers once per overlay handler. Recreated handlers get a fresh
  registration; reconnecting does not add duplicates. Disconnect and level changes
  clear markers and interpolation state immediately.
- Missing/changed optional API linkage during initialization or tick updates is
  logged and disables this adapter rather than changing train motion or identity.

The compile-only API defaults to the existing local
`libs/xaerominimap-neoforge-1.21.1-26.6.0.jar`; `-Pxaero_api_jar=...` can override it.
The dependency is not embedded in the release jar.

## Validation

`checkMinimapBridge` checks the actual installed API signatures and marker
interpolation, and verifies there are no entity-spawn, server-command or persistent
waypoint calls in the adapter. It runs as part of the full offline build.

Manual checks, when the user requests the client:

1. With Xaero installed, place a physical train and primary console. Check the
   marker and name, rename it, drive/reverse/turn, change map zoom/rotation and test
   both circular and square minimaps.
2. Couple several carriages and add a secondary console: the consist should still
   have one marker. Remove the final console and verify removal; add it again and
   verify it returns without duplicate markers.
3. Switch dimensions, disconnect/reconnect, and reload a parked train. Check that
   stale markers disappear and interpolation does not cross levels.
4. Check long and Chinese names, several nearby trains, minimap boundaries, and a
   hidden minimap. Check safe-mode rendering if that mode is in use.
5. Start without Xaero and without Electro Energetics in the development optional-
   dependency configurations; core train behavior must remain available.

No client was launched for this change. In-game marker rendering remains pending.
