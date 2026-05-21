# Testing Guide

This document describes the test framework, methodology, and conventions for the
FishMating plugin.

The suite currently has **53 tests** across 9 classes, covering **~85% of lines**
(~73% of branches). See [Coverage](#coverage) for the live report.

## Toolchain

| Tool | Version | Purpose |
|------|---------|---------|
| [JUnit 6](https://junit.org/) (Jupiter) | `6.1.0` (BOM) | Test framework and assertions |
| [MockBukkit](https://mockbukkit.org/) | `4.110.0` (`mockbukkit-v1.21`) | In-memory Bukkit/Paper server for integration tests |
| [JaCoCo](https://www.jacoco.org/jacoco/) | `0.8.14` | Code-coverage measurement and reporting |
| Maven Surefire | `3.5.5` | Runs the JUnit Platform during `mvn test` |

Versions are centralised as properties in `pom.xml`. The JUnit and Adventure stacks
are pinned via imported BOMs (`junit-bom`, `adventure-bom`) so every artifact in each
family resolves to a single, consistent version.

> **Why MockBukkit needs paper-api + adventure-api.** The plugin is compiled against
> `paper-api` (`provided`). Maven does not propagate a *provided* dependency's
> transitive dependencies, so `adventure-api` is declared directly (also `provided`)
> and aligned to the version `paper-api` ships (`adventure-bom 4.26.1`). MockBukkit
> reuses the same `paper-api`/Adventure classes at test time.

## Test methodology

We use two complementary layers:

### 1. Unit tests
Fast, isolated tests of plain logic. Example targets:
- `models/FishDataTest` — breeding-readiness state machine, cooldown and timeout rules.
- `models/BreedingPairTest` — pair membership and validity.

These classes wrap a Bukkit `Entity`. Rather than mock Paper's deep `Entity`
interface graph (which is impractical with Mockito), we use MockBukkit's lightweight
real `SimpleEntityMock`. Time-based logic is asserted with **boundary values**
(cooldown/timeout of `0`) instead of `Thread.sleep`, so tests stay fast and
deterministic — never assert on wall-clock delays.

### 2. Integration tests
Boot the real plugin on an in-memory MockBukkit server to verify wiring, behaviour,
and configuration end to end:
- `FishMatingPluginTest` — `onEnable()` constructs all managers, registers listeners,
  and schedules the periodic tasks.
- `managers/ConfigManagerTest` — the bundled `config.yml` is parsed into the expected
  settings and fish→seed mappings.
- `managers/FishManagerTest` — seed seeking/consumption, stack handling, fish-data
  lifecycle, and fish tracking.
- `managers/BreedingManagerTest` — pairing rules (range, same world, no double-pairing).
- `listeners/EntityListenerTest` — event-driven tracking and death cleanup.
- `commands/FishMatingCommandTest` — `/fishmating reload` and its permission gate.
- `utils/ParticleUtilsTest` — the exact particle types each effect emits.

The MockBukkit lifecycle is:
```java
@BeforeEach void setUp()   { server = MockBukkit.mock(); plugin = MockBukkit.load(FishMatingPlugin.class); }
@AfterEach  void tearDown(){ MockBukkit.unmock(); }
```

**Driving the scheduler.** The managers do their work in repeating tasks
(`runTaskTimer`), which first fire at tick 20. Advance them deterministically with
`server.getScheduler().performTicks(20L)` rather than sleeping. MockBukkit does not
simulate physics, so a fish only "reaches" a seed when spawned within consume range.

**Exercising event-driven tracking.** Fish discovery is event-driven (see below), so
tests fire the real events through the plugin manager instead of relying on internal
calls:
```java
server.getPluginManager().callEvent(new EntitySpawnEvent(fish));          // spawn
server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, fish));  // chunk load
server.getPluginManager().callEvent(new EntityDeathEvent(fish, source, drops)); // death
```

> **How fish are tracked (and why it matters for tests).** `FishManager` keeps a map
> of tracked fish that is populated *by events*, not by polling: `EntityListener`
> registers fish on `EntitySpawnEvent` and `EntitiesLoadEvent`, `onEnable` runs a
> one-time `trackExistingFish()` scan, and dead/invalid fish are pruned each update
> cycle. `BreedingManager` then iterates only this tracked set. Tests must therefore
> ensure a fish is tracked (via a spawn/load event, or `FishManager#trackFish`) before
> expecting the managers to act on it.

## Conventions

- **Location**: tests live under `src/test/java`, mirroring the package of the class
  under test. Name files `<ClassName>Test.java`.
- **Naming**: method names state behaviour (`cannotBreedDuringCooldown`); add a
  human-readable `@DisplayName` to every test.
- **One behaviour per test**; use Arrange–Act–Assert structure.
- **No flaky time dependence**: prefer boundary values over sleeping.
- Assert against the **actual bundled `config.yml`**, not documentation examples.

## Running the tests

```bash
mvn test            # compile and run the test suite
mvn verify          # tests + package the plugin jar
```

## Coverage

JaCoCo instruments the test JVM (`prepare-agent`) and writes a report during the
`test` phase. After a build:

- **HTML**: `target/site/jacoco/index.html`
- **XML/CSV**: `target/site/jacoco/jacoco.xml`, `jacoco.csv`

Coverage is **report-only** today — the build does not fail on a coverage threshold.
CI publishes the HTML report as the `jacoco-coverage-report` artifact on every run.
Once coverage stabilises, a ratcheting minimum can be enforced by adding a
`jacoco:check` execution with a `LINE` coverage rule in `pom.xml`.

## CI

`.github/workflows/build.yml` runs `mvn clean verify` on a JDK **21** and **25**
matrix for every push and pull request to `main`, then uploads the plugin jar and the
JaCoCo report as build artifacts.

It also runs **API compatibility guards** that compile the main sources against the
**1.21 floor** (`-Ppaper-floor`, JDK 21) and the **26.1 ceiling** (`-Ppaper-26`,
JDK 25). These compile-only checks fail fast if a symbol we use was added after 1.21.0
or removed by 26.1, keeping the single jar valid across the whole supported range.
