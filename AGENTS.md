# Project defaults

Before creating or changing any player-facing text, screen, HUD, tooltip, or configuration UI, read:

1. `docs/project-style.json` — canonical, machine-readable language and UI rules
2. `docs/PROJECT_STYLE.md` — interpretation, examples, implementation entry points, and review steps

Apply these rules throughout this repository, including new features. Use the terminology and shared Create theme defined there. Keep Chinese and English translations synchronized and describe implemented behavior accurately. Do not introduce a separate visual theme for new screens.

Explicit user instructions override these defaults. For a lasting style change, update the JSON specification, schema when its structure changes, and reader guide together. Keep rule IDs stable. If the documents disagree, use the JSON specification and correct the guide.

These conventions govern product content; they do not require developer logs, code identifiers, or conversation prose to imitate in-game text. Do not alter working gameplay just to make it fit a wording example.

For text/UI changes, run `gradlew.bat checkLanguage --offline` and the relevant build checks. Report visual verification separately from automated checks. Follow the user's testing workflow: list scenarios, launch the client when requested, inspect logs, collect observations, then plan the next change. Do not run a server for autonomous gameplay testing.

Do not launch or restart any Minecraft client, including main-menu capture or startup compatibility checks, unless the user explicitly asks to open the client. The user stopped automatic client launches after repeated interruptions; complete code, logs and offline checks first and wait for that explicit request.

# GitHub synchronization

The user has authorized uploading this project and subsequent work to `https://github.com/qwer854645/Create-Physical-Railways.git`. Keep `origin` pointed at this repository and use `main` as the published branch.

Commit completed, verified steps separately. At the end of a work step, check `git rev-list --count origin/main..HEAD`; when at least 3 commits are ahead, push with `git push origin main`. An explicit user request to upload means push immediately regardless of the count. Gameplay acceptance may still be pending; report that accurately rather than implying it passed. Do not force-push or overwrite remote work; fetch and reconcile changes first if a normal push is rejected. Preserve unrelated local files and keep ignored dependencies, worlds, logs, credentials, and build outputs out of commits.
