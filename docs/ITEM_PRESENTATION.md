# Item presentation

## Driver controls

The item model retains its geometry and native Create materials. Its inventory yaw
is 315 degrees (previously 135), with explicit first-person, third-person, dropped
and item-frame transforms. These are item display transforms only: block facing,
placement and physical collision are unchanged.

## Item descriptions

`ItemDescriptions` registers Create's `ItemDescription.Modifier` with its standard
palette through `TooltipModifier.REGISTRY` during client setup. Descriptions use
the native `.tooltip.summary`, `.conditionN` and `.behaviourN` translation keys.
Create handles the Shift hint, text wrapping, emphasis and language cache.

This covers the bogey, controls, coupler, coupling tool and optional rail docking
connector. Dynamic bogey style information appears only while Shift is held.
Retired unconditional tooltip methods and their multiline translation entries
have been removed.

## Coupling tool texture

Final asset: `src/main/resources/assets/guidedrailways/textures/item/coupler_link.png`.
The PNG is exactly 16 by 16 pixels with binary transparency. The preview in
`build/coupler-link-16-preview.png` enlarges each final pixel to a 16-pixel square.
The generated artwork was sampled at pixel centers onto the final 16 by 16 grid,
without smoothing; alpha was snapped to transparent or opaque.

Generation used the built-in imagegen tool, with this prompt:

> Create a Minecraft Create-mod-style item inventory sprite: a compact railway coupler linking tool, dark forged-steel open hook / spanner head at upper right, short warm brown handle toward lower left, one restrained brass pin. It must be composed on an EXACT 16 by 16 LOGICAL PIXEL grid with no subpixel detail: if the output canvas is larger, each logical pixel is one perfectly uniform square cell and all edges align with the same 16x16 grid. Full square canvas, transparent background. Single diagonal tool, readable simple silhouette, 1-cell dark outline, stepped silver highlights on upper-left edges, only 8 flat colors maximum plus transparent. Occupy cells 2 through 14 in both axes. No border, no text, no grids, no glow, no gradients, no anti aliasing, no shadow outside the sprite. Prioritize an authentic tiny vanilla Minecraft 16x16 item texture, not a detailed illustration. Output the sprite alone.

The generator did not enforce its requested logical grid exactly; the final PNG
dimensions were enforced and verified separately. No claim is made that the output
obeyed the prompt's eight-color limit.

## Acceptance

The offline build and language checks passed; the final PNG dimensions and enlarged
pixel preview were inspected. In-game verification is still needed for inventory
and both hands, Shift expansion in Chinese/English, and bogey style help. This
change does not launch, restart or reload an already-running client automatically.
