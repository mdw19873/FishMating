# 🐠 FishMating Plugin
> Bring your underwater world to life by letting fish breed naturally when players throw seeds into water! 🌊✨

![FishMating Logo](./logo256x256.png)

---

## 📦 Overview

**FishMating** is a feature-rich and highly configurable Minecraft plugin supporting **Minecraft 1.21.x through 26.1**. Crafted with ❤️ by `mrsuffix`, this plugin brings your aquatic biomes to life by introducing a unique, seed-based fish breeding mechanic.

Throw seeds into water to attract nearby fish! Fish will detect matching seeds within a **5-block radius**, swim toward them, and become *breeding-ready* after consuming a seed. If two ready fish find each other within **30 seconds**, they breed and spawn a cute baby fish 🐣 and drop a little experience, just like vanilla mob breeding. After breeding, fish wait **5 minutes** before breeding again.

Every detail can be customized in the `config.yml` — from detection radius to particles, cooldowns, and seed mappings! ⚙️

---

## ✨ Features

✅ Works with Minecraft **1.21.x through 26.1**  
✅ Attract fish by throwing seeds into water 🌱  
✅ Fully configurable detection radius, cooldowns, particles, and more  
✅ Heart particles show breeding readiness ❤️  
✅ Supports four fish types by default:  
- Salmon 🐟 ← *Wheat Seeds* 🌾  
- Cod 🐠 ← *Pumpkin Seeds* 🎃  
- Pufferfish 🐡 ← *Melon Seeds* 🍉  
- Tropical Fish 🐠 ← *Beetroot Seeds* 🥬  

✅ Bred tropical fish inherit a parent's pattern & colors 🎨  
✅ Successful breeding drops experience, matching vanilla (configurable, capped at vanilla's 1–7) ✨  

✅ Advanced controls: debug logging, max tracked fish, breeding success chance  
✅ Clean, modern multi-class code structure and JavaDocs 🛠️  
✅ Compatible with other popular plugins  
✅ Graceful handling of edge cases to keep your console error-free 🚀

---

## ⚙️ Configuration

Here’s an example `config.yml` with full customization options:

The file has two tunable sections: **`settings`** (basic gameplay knobs) and **`advanced`**
(anti-abuse, performance, integrations, and diagnostics most servers leave alone). The bundled
file documents every option in detail; here's the shape of it:

```yaml
settings:                        # basic gameplay tuning
  # ---- Breeding ----
  detection-radius: 5.0          # blocks; seed/partner detection range
  breeding-timeout-seconds: 30   # how long "ready to breed" lasts after eating a seed
  breeding-cooldown-minutes: 5   # wait between breeds (parents + newborn)
  breeding-success-rate: 1.0     # chance a ready pair produces a baby (0.0-1.0)
  breeding-experience: 7         # XP at the baby, random 1..value (0-7; 0 = off)
  # ---- Growth ----
  natural-growth: true           # babies spawn small and grow; false = full-size
  baby-scale: 0.5                # newborn size when natural-growth is on (0.1-1.0)
  growth-duration-minutes: 10    # baby -> adult time
  # ---- Effects ----
  enable-particles: true
  particle-count: 5

fish-mappings:                   # entity_type: seed_material
  salmon: wheat_seeds
  cod: pumpkin_seeds
  pufferfish: melon_seeds
  tropical_fish: beetroot_seeds

advanced:                        # ops / anti-abuse / integrations / diagnostics
  # ---- Anti-abuse / farm controls ----
  require-player-thrown-seeds: true  # ignore dispenser/dropper seeds
  require-player-within: 0           # blocks; require a player nearby (0 disables)
  # ---- Performance ----
  max-tracked-fish: 1000             # cap on tracked fish (see note below)
  # ---- Integrations ----
  worldguard-integration: false      # respect the "allow-fish-breeding" region flag
  # ---- Behavior ----
  inherit-persistence: false         # bred babies inherit a persisting parent's "won't despawn"
  # ---- Diagnostics ----
  debug-logging: false               # raise the log level to FINE
```

> **Upgrading from a pre-1.7.0 config?** `natural-growth`, `baby-scale`,
> `growth-duration-minutes`, and `breeding-success-rate` moved from `advanced` to `settings`.
> Move those four keys under `settings:` in your existing file — the plugin logs a warning on
> startup if it finds them still under `advanced` (where they're no longer read).

Everything from detection range to particle effects and breeding logic can be tweaked! 🎛️

> **`max-tracked-fish`** caps how many fish the plugin tracks simultaneously. Fish are
> tracked as they spawn or their chunks load; once the cap is reached, additional fish
> are simply not tracked (they won't seek seeds or breed) until tracked fish are freed —
> e.g. when they die or their chunk unloads.
>
> Since **1.6.0**, seed seeking is event-driven (a seed landing in water attracts nearby
> fish; there is **no per-fish, per-tick world scan**), so the real cost of a tracked fish is
> now very small: roughly **150–200 bytes** of memory each (≈ 0.2 MB at 1000) and a cheap
> per-tick state check. The default **1000** keeps things well bounded for typical setups,
> but large aquatic/ocean servers can raise it to **several thousand** with negligible CPU or
> memory impact — lower it only if you want to be extra conservative on constrained hardware.

> **`natural-growth`** makes bred fish spawn at `baby-scale` and grow to full size over
> `growth-duration-minutes`. A fish can't breed until it's full-grown, and growth only
> advances while it's loaded (so it pauses when its chunk unloads and resumes afterward).
> Just like vanilla baby animals, a not-yet-grown fish drops no loot or experience if
> killed — so newborns can't be farmed for drops before they mature. A not-yet-grown fish
> also can't be captured in a bucket (vanilla buckets would reset it to full size on
> placement, skipping the growth gate); let it mature first. Set `natural-growth: false`
> for full-size offspring with no growth phase.

> **`require-player-thrown-seeds`** (default **true**) makes only seeds *thrown by a
> player* attract fish, so dispenser/dropper-fed contraptions can't run automated
> breeding/XP farms. Set it to `false` to let any dropped seed work.

> **`require-player-within`** (default **0**, disabled) requires a non-spectator player
> within the given block radius for fish to seek seeds or breed. Set a radius (e.g. `32`)
> to stop unattended / chunk-loader farms from running while no one is around.

> **`worldguard-integration`** (default **false**) turns on optional [WorldGuard](https://dev.bukkit.org/projects/worldguard)
> support. When enabled (and WorldGuard is installed), breeding obeys the custom
> `allow-fish-breeding` region flag: fish won't produce offspring in regions where it's set
> to `DENY`. The flag defaults to `ALLOW`, so breeding works everywhere unless a region
> opts out — e.g. `/rg flag <region> allow-fish-breeding deny`. The flag is registered
> automatically whenever WorldGuard is present; this option only controls enforcement, and
> it has no effect when WorldGuard isn't installed.

> **`inherit-persistence`** (default **false**) makes a bred baby *inherit* persistence: if
> at least one parent already won't despawn — like a fish placed from a bucket, which vanilla
> flags `PersistenceRequired` — the newborn won't despawn either. Offspring of ordinary wild
> fish still despawn as normal, so this only carries the trait forward through lineages a
> player has deliberately invested in (and keeps wild-fish breeding farms bounded).

---

## 🛠 Installation

1️⃣ Download **FishMating.jar** from the [latest release](https://github.com/mdw19873/FishMating/releases/latest).
2️⃣ Place it in your server's `/plugins` folder.
3️⃣ Restart or reload your server.
4️⃣ Edit the `config.yml` to fit your server’s style!
5️⃣ Enjoy dynamic underwater life! 🌊🐟

---

## 🧑‍✈️ Commands & Permissions

| Command | Description | Permission |
|---------|-------------|------------|
| `/fishmating reload` (alias `/fm`) | Reloads `config.yml` without a server restart | `fishmating.admin` |
| `/fishmating status` | Summarises the tracked fish: total vs the cap, a per-type breakdown, and how many are breeding-ready, seeking a seed, on breeding cooldown, or still growing, plus the active breeding-pair count | `fishmating.admin` |
| `/fishmating nearby [radius]` | (Players only) Lists tracked fish near you with type, distance, maturity, and breeding state. Radius defaults to `detection-radius` and is capped at 64 | `fishmating.admin` |
| `/fishmating config` | Prints the live (post-clamp) configuration values, so you can confirm what's actually in effect after a reload | `fishmating.admin` |
| `/fishmating grow <radius\|all>` | Forces tracked fish to full size (scale 1.0). `all` grows every tracked fish (works from console); `<radius>` (players only) grows those within range. Already-grown fish are skipped | `fishmating.admin` |

`fishmating.admin` defaults to **op**.

---

## 📂 Project Structure

```
FishMating/
├── src/
│   ├── main/
│   │   ├── java/org/mrsuffix/fishmating/   # package: com.mrsuffix.fishmating (dir ≠ package, by design)
│   │   │   ├── FishMatingPlugin.java       # Main plugin class (entry point)
│   │   │   ├── commands/                   # /fishmating command + tab completion
│   │   │   ├── listeners/                  # Event listeners (spawn, death, item-drop, bucket)
│   │   │   ├── managers/                   # ConfigManager, FishManager, BreedingManager
│   │   │   ├── models/                     # FishData, BreedingPair
│   │   │   ├── integrations/               # Optional soft-deps (WorldGuard)
│   │   │   └── utils/                       # Helper classes
│   │   └── resources/
│   │       ├── plugin.yml
│   │       └── config.yml
│   └── test/                                # JUnit 6 + MockBukkit test suite
└── README.md
```

Well-documented and clean to help new developers understand and contribute! 🧰

---

## 🧪 Building & Testing

Built with Maven against **Java 21** and `paper-api` 1.21.11. The plugin uses only
stable API, so one jar runs on **Minecraft 1.21.x through 26.1**; CI compile-guards
verify it against both the 1.21 floor and the 26.1 ceiling (26.1 servers run Java 25).

```bash
mvn verify   # compile, run the test suite, and package the plugin jar
```

The project has an automated test suite (JUnit 6 + MockBukkit) with JaCoCo coverage
reporting, and CI builds/tests on **Java 21 and 25**. See **[TESTING.md](./TESTING.md)**
for the full testing methodology and conventions.

Releases follow [Semantic Versioning](https://semver.org/); see
**[RELEASING.md](./RELEASING.md)** for the release process and
**[CHANGELOG.md](./CHANGELOG.md)** for the version history.

---

## 📜 License

This plugin is open-source under the **MIT License**.
Feel free to use, modify, and share! 🤝

---

## ✏️ Author

Developed with ❤️ by **mrsuffix**

> GitHub: [mrsuffix](https://github.com/mrsuffixx)

### 🤝 Contributors

- **MDW** — contributor · GitHub: [mdw19873](https://github.com/mdw19873)

---

## ⭐ Support the Project!

If you enjoy **FishMating**, leave a ⭐ on the repository!
Your support helps keep the project alive and encourages new updates! 🚀✨

---

## 🌊 Bring life to your oceans!

FishMating makes your aquatic world dynamic, fun, and interactive.
Perfect for survival servers, creative builds, and roleplay worlds alike.
Make your underwater biomes feel truly alive! 🐟❤️🌱
