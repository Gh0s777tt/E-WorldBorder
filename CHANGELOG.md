# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to semantic versioning.

## [2.0.0] - 2026-06-12

The first release of the Kotlin rewrite (forked from Brettflan's WorldBorder; last upstream change was in 2020).

### Changed
- Full rewrite from Java to **Kotlin**; build moved from Maven to **Gradle Kotlin DSL** (Shadow fat-jar + wrapper).
- Targets **Paper 1.21 / Java 21** (was Spigot 1.14 / Java 8).
- Async chunk loading via Paper's native `getChunkAtAsync`; force-loading via plugin chunk tickets.
- Messaging migrated to **Adventure** + **MiniMessage**.
- Scheduling migrated to the modern Paper schedulers — **Folia-compatible**.

### Added
- Native (client-side) world-border integration: `/wb vanillaborder <on|off>`, with smooth animated resizes.
- **PlaceholderAPI** expansion and optional **bStats** metrics.
- Tab completion for all subcommands, world names and values.
- GitHub Actions CI (build + lint), release-on-tag workflow, and unit tests for the border math.

### Fixed
- Mojang UUID/name lookups (`bypass`/`bypasslist`) no longer block the main thread.
- Replaced the removed Mojang name-history endpoint with the server `OfflinePlayer` cache (+ session-server fallback).
- Thread-safe UUID caches and coordinate formatting.
- Division-by-zero in the border radius math.
- Version-proof "safe spot" detection (`Block.isPassable`, `world.minHeight/maxHeight`).
- Removed an explicit `System.gc()` from the Fill task.

[2.0.0]: https://github.com/Gh0s777tt/E-WorldBorder/releases/tag/v2.0.0
