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

```yaml
# FishMating Plugin Configuration
# Author: mrsuffix
# Version: ${project.version}

settings:
  detection-radius: 5.0
  breeding-timeout-seconds: 30
  breeding-cooldown-minutes: 5
  breeding-experience: 7
  enable-particles: true
  particle-count: 5

fish-mappings:
  salmon: wheat_seeds
  cod: pumpkin_seeds
  pufferfish: melon_seeds
  tropical_fish: beetroot_seeds

advanced:
  # Raise the plugin log level to FINE for debug diagnostics
  debug-logging: false
  # Upper bound on how many fish are tracked at once (see note below)
  max-tracked-fish: 1000
  # Bred fish spawn small and grow to full size over time
  natural-growth: true
  # Starting size of a bred fish, 0.1-1.0 (used when natural-growth is on)
  baby-scale: 0.5
  # Minutes for a baby to grow to full size
  growth-duration-minutes: 10
  # Chance a ready pair produces a baby, 0.0-1.0
  breeding-success-rate: 1.0
  # Only player-thrown seeds attract fish (blocks dispenser/dropper auto-farms)
  require-player-thrown-seeds: true
  # Require a player within N blocks for fish to seek/breed; 0 disables
  require-player-within: 0
  # Respect the WorldGuard "allow-fish-breeding" region flag (needs WorldGuard)
  worldguard-integration: false
````

Everything from detection range to particle effects and breeding logic can be tweaked! 🎛️

> **`max-tracked-fish`** caps how many fish the plugin tracks simultaneously. Fish are
> tracked as they spawn or their chunks load; once the cap is reached, additional fish
> are simply not tracked (they won't seek seeds or breed) until tracked fish are freed —
> e.g. when they die or their chunk unloads. This bounds memory and CPU on busy servers.
> Raise it for large aquatic servers, or lower it to be more conservative.

> **`natural-growth`** makes bred fish spawn at `baby-scale` and grow to full size over
> `growth-duration-minutes`. A fish can't breed until it's full-grown, and growth only
> advances while it's loaded (so it pauses when its chunk unloads and resumes afterward).
> Set `natural-growth: false` for full-size offspring with no growth phase.

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

---

## 🛠 Installation

1️⃣ Download **FishMating.jar** from the [latest release (v1.3.0)](https://github.com/mdw19873/FishMating/releases/tag/v1.3.0).
2️⃣ Place it in your server's `/plugins` folder.
3️⃣ Restart or reload your server.
4️⃣ Edit the `config.yml` to fit your server’s style!
5️⃣ Enjoy dynamic underwater life! 🌊🐟

---

## 🧑‍✈️ Commands & Permissions

| Command | Description | Permission |
|---------|-------------|------------|
| `/fishmating reload` (alias `/fm`) | Reloads `config.yml` without a server restart | `fishmating.admin` |

`fishmating.admin` defaults to **op**.

---

## 📂 Project Structure

```
FishMating/
├── src/
│   ├── main/java/com/mrsuffix/fishmating/
│   │   ├── FishMating.java          # Main plugin class
│   │   ├── listeners/               # Event listeners
│   │   ├── managers/                # Logic and tracking
│   │   └── utils/                   # Helper classes
├── resources/
│   ├── plugin.yml
│   └── config.yml
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
