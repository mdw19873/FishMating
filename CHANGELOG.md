# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
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

[Unreleased]: https://github.com/mdw19873/FishMating/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/mdw19873/FishMating/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/mdw19873/FishMating/releases/tag/v1.1.0
