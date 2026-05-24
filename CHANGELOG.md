# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Fixed
- Closed a growth-gate bypass: a not-yet-grown fish can no longer be captured in a bucket.
  Vanilla fish buckets don't persist the `scale` attribute, so a bucketed baby would respawn
  at full size on placement, letting a player skip the growth-time gate (breed → bucket the
  baby → place an instant adult). Bucketing an immature fish is now blocked with a message;
  the baby grows where it is. Adults bucket normally, and this is a no-op when
  `natural-growth` is off (no fish is ever shrunk, so all read full-grown).

## [1.5.1] - 2026-05-24
### Changed
- The `/fishmating status`, `nearby`, and `config` command output now colours the label
  (key) and value distinctly for easier scanning, with a bold section heading. In `nearby`,
  each fish's breeding state is colour-coded (ready = green, cooldown = yellow, seeking =
  aqua, idle = gray).

## [1.5.0] - 2026-05-24
### Added
- New read-only admin query sub-commands under `/fishmating` (alias `/fm`), all gated by
  `fishmating.admin`:
  - `status` — aggregate summary of the tracked fish: total vs `max-tracked-fish`, a
    per-type breakdown, and counts of breeding-ready / seeking / still-growing fish, plus
    the number of active breeding pairs.
  - `nearby [radius]` — (players only) lists tracked fish near the caller with type,
    distance, maturity, and breeding state. Radius defaults to `detection-radius` and is
    capped at 64 blocks.
  - `config` — dumps the live (post-clamp) configuration values to confirm what is in
    effect after a reload.
- New admin sub-command `/fishmating grow <radius|all>` (also `fishmating.admin`): forces
  tracked fish to full size (scale 1.0). `all` grows every tracked fish (usable from the
  console); `<radius>` (players only) grows those within range. Already-full-grown fish are
  skipped so no redundant scale-update packets are sent.

## [1.4.1] - 2026-05-23
### Fixed
- A not-yet-grown fish (one still below full size during the `natural-growth` phase) now
  drops **no loot and no experience** when killed, matching vanilla baby animals. Because
  the mapped fish aren't `Ageable`, a "baby" is only a shrunk-scale adult to the server, so
  it previously dropped full loot + kill XP the instant it spawned — letting a player breed
  fish and immediately kill the newborns to harvest drops/XP and bypass the growth-time
  gate. Drops resume normally once the fish matures. (No effect when `natural-growth` is
  off, since fish are never shrunk and so always count as full-grown.)

## [1.4.0] - 2026-05-23
### Added
- `advanced.worldguard-integration` (default false): optional WorldGuard support. When on
  (and WorldGuard is installed), breeding respects the custom `allow-fish-breeding` region
  flag — fish won't produce offspring in regions where the flag is set to `DENY`. The flag
  defaults to `ALLOW`, so breeding works everywhere unless a region opts out. The plugin
  registers the flag whenever WorldGuard is present; the config option only controls
  enforcement. No effect when WorldGuard isn't installed.
- `advanced.inherit-persistence` (default false): when on, a bred baby inherits "won't
  despawn" persistence if at least one parent already persists (e.g. a fish placed from a
  bucket), mirroring vanilla's `PersistenceRequired` flag. Wild-fish offspring still
  despawn, so the trait only carries forward through player-invested lineages.

### Changed
- Breeding-ready fish now emit a single heart burst when they eat a seed (alongside the
  existing consumption particles), instead of a continuous stream of hearts for the whole
  readiness window. This matches how love-mode hearts actually appear in current Java
  Edition (shown once, per the long-standing bug
  [MC-93826](https://bugs.mojang.com/browse/MC-93826)) and removes a per-fish, per-tick
  particle packet. The mating and birth particle effects are unchanged.

## [1.3.0] - 2026-05-23
### Added
- `advanced.require-player-thrown-seeds` (default true): only seeds thrown by a player
  attract fish, so dispenser/dropper-based automated breeding farms no longer work. Set
  it to false to restore the previous "any dropped seed" behavior.
- `advanced.require-player-within` (default 0 = off): when set, fish only seek seeds and
  breed if a non-spectator player is within that many blocks, blocking unattended /
  chunk-loader farms.
- `advanced.natural-growth` now works: bred fish spawn at `advanced.baby-scale` (default
  0.5) and grow to full size over `advanced.growth-duration-minutes` (default 10). A fish
  can't breed until it is full-grown. Growth resumes correctly after a restart.

### Changed
- `advanced.breeding-success-rate` is now enforced (previously ignored): it sets the
  chance a ready pair produces a baby. A failed attempt still applies the cooldown.
- `advanced.debug-logging` is now enforced (previously ignored): when on, it raises the
  plugin log level so the existing debug diagnostics are emitted.

## [1.2.0] - 2026-05-23
### Added
- Successful breeding now drops experience at the baby, mirroring vanilla mob breeding.
  The amount is a random 1 up to `settings.breeding-experience` (default 7), hard-capped
  at vanilla's 7; set it to 0 to disable.

### Changed
- Newly bred fish now start on the breeding cooldown, just like their parents, so a
  player can't chain-breed offspring to bypass the cooldown.
- Default `breeding-cooldown-minutes` raised from 3 to 5, matching vanilla animal breeding.

## [1.1.0] - 2026-05-20
### Added
- Support for **Minecraft 1.21.x through 26.1** from a single jar (built against
  `paper-api` 1.21.11 on **Java 21**; note that 26.1 servers run on Java 25).
- CI compile-guards validating the main sources against both the **1.21 floor** and
  the **26.1 ceiling** APIs, so the supported range can't silently drift.
- Automated test suite (JUnit 6 + MockBukkit) with JaCoCo coverage; CI builds and
  tests on Java 21 and 25.
- `/fishmating reload` (alias `/fm`) admin command, gated by the `fishmating.admin`
  permission, to reload `config.yml` without a restart.
- Enforcement of `advanced.max-tracked-fish` to bound how many fish are tracked.
- Bred tropical fish inherit a parent's pattern and colors.

### Changed
- `api-version` is `1.21`, so the plugin loads on any 1.21.x server as well as 26.1.
- Fish discovery is event-driven (spawn / chunk-load events) instead of polling every
  entity in every world each tick.
- Particle effects use valid 1.21 particle constants.

### Fixed
- Seed stacks are correctly decremented when a fish consumes a seed.
- Cross-world breeding checks no longer throw and abort the cycle.
- Breeding-pair selection no longer skips fish.
- Config loading is resilient to a missing `fish-mappings` section or a blank seed value.

[Unreleased]: https://github.com/mdw19873/FishMating/compare/v1.5.1...HEAD
[1.5.1]: https://github.com/mdw19873/FishMating/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/mdw19873/FishMating/compare/v1.4.1...v1.5.0
[1.4.1]: https://github.com/mdw19873/FishMating/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/mdw19873/FishMating/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/mdw19873/FishMating/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/mdw19873/FishMating/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/mdw19873/FishMating/releases/tag/v1.1.0
