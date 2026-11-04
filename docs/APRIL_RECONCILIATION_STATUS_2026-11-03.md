# Phantasia Infinity — April source reconciliation (2026-11-03)

## Status

This working branch is based on `main` at `0145dbee522640cc2a306a60046e9df7587e2078`.
The original state is preserved by `archive/pre-reconciliation-2026-11-03`.
Do not merge until the April source is imported and the core/build checks are green.

## April source provenance

The user's `Phantasia(6).txt` text export is labeled 2026-04-26 01:04 and contains 249 Java files.
The SHA-256 checksum of the original listing is:
`b703d4e486e2c5e39bd2f9bb2dbe6044c8a326160284756b6fa2d3d4dc2c415c`.

Reconstruction findings:
- 249 Java files extracted.
- 220 Java file paths already exist on the original GitHub `main`; 29 paths are April-only.
- All 134 April `phantasia-core` Java sources compile with `javac --release 21`, without external engine libraries.
- A standalone, dependency-free baseline smoke test passes 17 checks, including the April SaveManager v4 round trip.
- Core and J2D sources also compile together using javac 21. The editor, JME, libGDX, and Godot frontends were not rebuilt or tested.

The April source itself is **not yet committed to this branch**. Its extraction bundle and guarded import script were prepared separately, because importing the source needs verification in a working checkout.

## Decisions

- Treat the April export as the primary source baseline after reconciliation.
- Redesign the 64-byte DataCore with a common header and type-specific layouts.
- Simplify stored character statistics; keep time and weather.
- Use a new save format (provisionally v5); defer historical save import.
- Enforce strict town-only saves; no automatic recovery snapshot.
- Require automated regressions in small, green increments.
- Keep the headless Java core; retain game data and editor tools.
- Archive JME, J2D, and libGDX frontends only after dependencies are mapped; keep `main` intact.

## Known issues

- `PC_XP` at `0x20..0x21` overlaps `PC_EQUIP_WEAPON` at `0x21`.
- Limb-status bits at `0x1D..0x1E` overlap 16-bit magic-power fields.
- April's `SaveManager` is still format v4 and has no town-only access gate.
- Core has Swing tooling under `com.phantasia.core.tools` and is not yet Android-portable as a module.
- The editor imports some of those Swing classes and depends on J2D.
- A solo CombatConsoleRunner run completed; its message expected 2 enemies, but the generated encounter contained 1 (future encounter-scaling regression).\n- The original GitHub tree has no automated test sources.

## Next steps

1. Import April Java source on this working branch, preserving Gradle files and game data.
2. Run the Java 21 core baseline smoke checks and audit the import diff.
3. Move editor GUI code out of core in a separate increment.
4. Specify the collision-free 64-byte typed layout, then implement it and tests.
5. Implement versioned v5 saves with safe rejection of old formats and strict town-only enforcement.

No deletion or cleanup of legacy frontends should precede a preserved reference and passing core checks.
