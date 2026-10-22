# Ponder tutorials

Four native Ponder storyboards are registered by the client-only
`RailwayPonderPlugin`:

- Physical Bogey: choose a style, place directly on track, build/glue the carriage,
  supply rotation through the top mount, rerail and dismantle with the wrench.
- Physical Train Controls: install on the physical body, enter driving before
  holding right-click, adjust the lever, select branches, brake/reset, edit settings,
  insert a schedule, and explicitly take control from observation mode.
- Electric Driving: the next page on the controls item, explicitly ordered after
  manual driving. Shows pantographs, catenary construction, DC supply/ground,
  energized contact, manual takeover and traction, power/load limits, supply loss
  and braking. Registered only when Electro Energetics is installed, using its
  native Ponder wire elements and pantograph renderer. No real circuit is created.
- Rail Docking Connector: install matching endpoints, select the world endpoint
  with a wrench, bind a station, associate an addressed mailbox, stop and dock,
  transfer through real handling facilities, inspect and release before departure.
  This storyboard is registered only when Simulated is present.

Hover over the corresponding item and use Ponder's displayed key binding to open
the tutorial. The normal Create Shift description and Ponder entry coexist.

## Resources and behavior

`assets/guidedrailways/ponder/*.nbt` are self-contained original 9 by 7 by 9 vanilla
structure templates. Regenerate them with
`python verification/generate_ponder_scenes.py`; no world or game process is needed.
The script writes only these four templates, with deterministic gzip metadata.

The bogey's stored cell is separate from the carriage. Its independent display
section is lowered to the actual rail/tread contact height, and moves alongside
the mount. Wheel phase uses the existing motion data; controls use their existing
visual notch data. Docking only animates native extension/feet and a visual lock;
it never pairs live connectors, requests Sable constraints or transfers real items.
The sections illustrate the operation without invoking the gameplay backend.

Header keys are `guidedrailways.ponder.<scene>.header`. Stable shared captions use
`guidedrailways.ponder.shared.<caption>`, registered from the English resource as
fallback text. Chinese and English are maintained together in the normal language
resources; default-language changes do not require duplicating captions in Java.

## Validation

`checkPonderAssets` decodes the compressed templates with Minecraft's NBT reader,
checks bounds, unique positions, palette references, track/vehicle layout and the
actual registered bilingual captions. It is part of the normal build/check task.
Compilation and language checks do not verify a Ponder rendering session.

Manual acceptance, once the user requests the client:

1. Open all three items' Ponder entries and verify Chinese and English text, both
   normal and smaller GUI scales; captions and mouse glyphs should remain readable.
2. Play, pause, scrub backward and restart each scene. Verify wheel/track alignment,
   carriage motion, lever positions, and extension/retraction of both connectors.
3. Verify the controls tutorial demonstrates entering first, holding right-click
   second, and emergency reset only after stopping. Observation must be described
   separately from manual takeover.
4. Verify the connector selection/binding order and mailbox address explanation.
   A chest is connected by a real funnel; no claim of remote train-wide inventory
   access is made. External redstone power is distinguished from automatic docking.
5. In a development environment without Simulated, verify bogey and controls still
   have tutorials and no connector tutorial is registered. No optional class should
   be required by the ordinary storyboards.
6. With Electro Energetics installed, open the controls item and confirm electric
   driving follows manual driving. Check both pantographs and the overhead wires
   on playback, rewind and restart. Without it, the original manual-driving page
   remains available without an empty electric page.

No client or server was started for authoring these tutorials; visual acceptance
remains pending.
