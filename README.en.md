# Create: Physical Railways

[简体中文](README.md) · **English**

Create: Physical Railways is a railway addon built on **Create** and **Sable**. Build your own physics-based carriages, fit them with bogeys and train controls, couple them together, and drive them on Create tracks manually or with schedules.

The project aims to bring physical trains into Create's existing railway gameplay while retaining free-form construction, physical collisions, and movement between carriages.

## Features

- **Build your own carriages:** construct bodies from blocks, with multiple physical bogeys supported under one body.
- **Power and driving:** supply Create rotational power and use the controls' lever for traction, coasting, and braking, alongside an emergency stop and a left/right junction selector.
- **Coupled trains:** connect separate carriages with couplers and the coupling tool. The primary controls command all supported bogeys in the train, and trailing carriages follow the leading carriage through junctions.
- **Scheduled operation:** install a Create schedule in the controls to set destinations, throttle limits, and wait conditions. Pause, resume, skip, or restart a schedule.
- **Railway integration:** connect with stations, signals, train observers, and display links for station presence, occupancy, destinations, and journey information.
- **Cargo and passengers:** read items and fluids from actual onboard containers and detect players in seats. Supported transfer interfaces enable cargo wait conditions and package tasks.
- **In-game guidance:** Chinese and English Ponder tutorials cover construction, driving, coupling, schedules, and integrations.

## Installation

For **Minecraft 1.21.1 / NeoForge**. The current development dependency set is:

- NeoForge **21.1.264**
- Create **6.0.10** (development build: 6.0.10-280)
- Sable **2.0.6**

Install this mod and its required dependencies in the client and server `mods` folders, including any additional dependencies required by Create and Sable. Use the same version of Physical Railways on both sides.

See [dependency notes](docs/DEPENDENCIES.md) for other checked versions and compatibility boundaries. The development dependency set does not mean every combination has passed in-game testing.

## Optional integrations

- **Steam ’n’ Rails:** wide, narrow, and monorail tracks, with corresponding bogey styles.
- **Blocks & Bogies:** bogey style selection, models, and animations.
- **Electro Energetics:** overhead wire and pantograph-powered traction; full in-game acceptance testing is still pending.
- **Simulated, VS / Sable Hose Connectors, and Automated Logistics:** item, fluid, and docking support within each interface's actual transfer scope. Package tasks require suitable docking facilities.
- **Xaero's Minimap:** physical train markers; visual verification is still pending.

These mods are optional; you do not need to install all of them. See the notes on [Create railway compatibility](docs/CREATE_COMPATIBILITY.md), [bogey and electric integrations](docs/ADDON_COMPATIBILITY.md), and [cargo access boundaries](docs/CARGO_ACCESS_BOUNDARY.md) for details. These reference documents are currently in Chinese.

## Getting started

Open **Ponder** on the physical bogey, coupler, coupling tool, and train controls items for construction and operating instructions. Some integration tutorials appear only when the corresponding mod is installed.

Aim directly at the part you want to operate: hold right-click and drag the lever or use the scroll wheel to change notches, right-click the emergency stop or junction selector, and sneak-right-click the controls to open settings.

## Status and limitations

The project is in **Alpha**. Use a backed-up world or a dedicated test world. Save recovery, multiplayer behavior, long-term performance, and some optional integrations need further in-game testing. See the [release acceptance record](docs/RELEASE_ACCEPTANCE.md) for the current verification scope.

- Physical trains cannot travel between dimensions.
- Create stations do not assemble or disassemble physical trains, or automatically hand schedules to and from them.
- Manual driving reports signal occupancy but does not enforce stops at red signals.
- Create's Portable Storage and Portable Fluid Interfaces cannot directly transfer cargo to or from physical bodies. Actual transport requires a supported transfer path.
- Third-party schedule instructions, wait conditions, and railway control interfaces are not universally supported.
- The mod ID is now `physicalrailways`; worlds using the previous namespace have no content migration path.

Please report problems through [Issues](https://github.com/qwer854645/Create-Physical-Railways/issues), including the mod version, dependency list, reproduction steps, and logs. Screenshots or videos are useful for visual and interaction issues.

## AI assistance and license

AI tools assisted with code, documentation, and some art assets in this project.

This project uses the [MIT License](LICENSE). Third-party mods and their assets remain subject to their respective licenses.
