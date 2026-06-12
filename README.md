# E-WorldBorder

[![Build](https://github.com/Gh0s777tt/E-WorldBorder/actions/workflows/build.yml/badge.svg)](https://github.com/Gh0s777tt/E-WorldBorder/actions/workflows/build.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)
![Paper](https://img.shields.io/badge/Paper-1.21-orange)
![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk)
![License](https://img.shields.io/badge/license-BSD-blue.svg)

An efficient, feature-rich plugin for limiting the size of your Minecraft worlds — knock players back at the border, and pre-generate (fill) or trim the map to the border.

This is a **Kotlin rewrite** of the (abandoned) [WorldBorder](https://www.spigotmc.org/resources/worldborder.60905/) plugin by Brettflan, modernized for **Paper 1.21+ / Java 21**, with the original's known bugs fixed and a set of modern features added.

## Features

- **Elliptic or rectangular** borders, global or per-world, with per-world shape/wrapping overrides.
- **Knockback** enforcement, with optional vehicle remounting and a "whoosh" particle/sound effect.
- **Fill** (pre-generate the world out to the border, async via Paper's chunk API) and **Trim** (delete chunks outside it).
- 🟥 **Native border integration** — optionally mirror the border onto Minecraft's built-in world border so players *see* the client-side red wall (`/wb vanillaborder on`); animates smoothly when you resize.
- 🎨 **MiniMessage** support in the border message (gradients, hover, click) — legacy `&` codes still work.
- 🧵 **Folia-compatible** — uses the modern Paper region/async/entity schedulers, so it runs on both Paper and Folia.
- 🔌 **PlaceholderAPI** expansion and optional **bStats** metrics.
- ⌨️ **Tab completion** for every subcommand, world names, and values.
- 🗺️ **DynMap** integration to show borders on the web map.

## Requirements

- **Paper 1.21+** (or a compatible fork such as Folia). Uses the Paper async-chunk, scheduler and Adventure APIs.
- **Java 21+** at runtime.

## Building

A Gradle wrapper is included, so you don't need Gradle installed:

```bash
./gradlew build        # macOS/Linux
.\gradlew build        # Windows
```

The plugin jar is produced at `build/libs/E-WorldBorder-2.0.0.jar` (a fat jar with a relocated Kotlin stdlib + bStats bundled in, so it won't clash with other plugins). Unit tests run as part of the build.

> The build targets Java 21 bytecode but runs fine on a newer JDK (it was built and verified against the real Paper 1.21.4 API on JDK 25 with Gradle 9.5.1). Tagging a release as `vX.Y.Z` builds the jar and publishes it to GitHub Releases automatically.

## Installing

Drop `E-WorldBorder-2.0.0.jar` into your server's `plugins/` folder and restart. Config lives in `plugins/E-WorldBorder/config.yml` (it is self-documenting — the important options carry inline comments).

## Usage

The single command is `/wb` (aliases: `/worldborder`, `/wborder`). Run `/wb` or `/wb help` for the full, paginated command list. Common examples:

```
/wb set <radiusX> [radiusZ]            - set a border centered on you
/wb [world] set <radiusX> [radiusZ] <x> <z>
/wb [world] fill [freq] [pad] [force]  - generate the world out to the border
/wb [world] trim [freq] [pad]          - trim chunks outside the border
/wb shape <elliptic|rectangular>       - default border shape
/wb knockback <distance>               - how far inside to knock players back
/wb vanillaborder <on|off>             - show the border as the client-side red wall
/wb bypass [player] [on|off]           - let a player cross the border
```

Permissions use the `worldborder.*` nodes (default `op`); see `plugin.yml`.

### PlaceholderAPI

With PlaceholderAPI installed, these resolve against the player's current world border:

```
%worldborder_radius%   %worldborder_radiusx%  %worldborder_radiusz%
%worldborder_centerx%  %worldborder_centerz%  %worldborder_shape%
%worldborder_wrapping% %worldborder_world%    %worldborder_bypassing%
```

## What changed vs. the original

**Language / build / platform**
- Full port from Java to **Kotlin**; Maven → **Gradle Kotlin DSL** (+ Shadow fat-jar + wrapper).
- Target moved from Spigot 1.14 to **Paper 1.21 / Java 21**.
- Async chunk loading uses Paper's native `getChunkAtAsync`; force-loading uses **plugin chunk tickets**.
- Messaging migrated to the **Adventure API** (+ MiniMessage); scheduling migrated to the modern Paper schedulers (**Folia-ready**).
- GitHub Actions **CI** (build + lint) and **release-on-tag**; unit tests for the border math.

**Bug fixes**
- The `bypass` / `bypasslist` Mojang UUID/name lookups no longer run on the main thread (the original could freeze the server when resolving offline players).
- The removed Mojang "name history" endpoint is replaced by the server's `OfflinePlayer` cache (+ a session-server fallback).
- UUID caches are now thread-safe (`ConcurrentHashMap`).
- Fixed a division-by-zero in the border radius math.
- "Safe teleport spot" detection uses `Block.isPassable` and `world.minHeight/maxHeight` instead of a hand-maintained, version-bound block list — so it keeps working across MC versions.
- Removed an explicit `System.gc()` call from the Fill task; coordinate formatting is now thread-safe.

## Credits & License

Original plugin and algorithms by **Brettflan** and contributors. Round-border math from rBorder by Reil; elliptical knockback by Lang Lukas; spiral-fill and others as credited in the source.

Released under the **BSD license** — see [LICENSE](LICENSE). As required, this fork keeps a link back to the original project: https://github.com/Brettflan/WorldBorder
