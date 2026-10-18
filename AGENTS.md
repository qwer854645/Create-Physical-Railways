# Project defaults

Before creating or changing any player-facing text, screen, HUD, tooltip, or configuration UI, read:

1. `docs/project-style.json` — canonical, machine-readable language and UI rules
2. `docs/PROJECT_STYLE.md` — interpretation, examples, implementation entry points, and review steps

Apply these rules throughout this repository, including new features. Use the terminology and shared Create theme defined there. Keep Chinese and English translations synchronized and describe implemented behavior accurately. Do not introduce a separate visual theme for new screens.

Explicit user instructions override these defaults. For a lasting style change, update the JSON specification, schema when its structure changes, and reader guide together. Keep rule IDs stable. If the documents disagree, use the JSON specification and correct the guide.

These conventions govern product content; they do not require developer logs, code identifiers, or conversation prose to imitate in-game text. Do not alter working gameplay just to make it fit a wording example.

For text/UI changes, run `gradlew.bat checkLanguage --offline` and the relevant build checks. Report visual verification separately from automated checks. Follow the user's testing workflow: list scenarios, launch the client when requested, inspect logs, collect observations, then plan the next change. Do not run a server for autonomous gameplay testing.
