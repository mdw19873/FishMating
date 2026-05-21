# 🐠 FishMating Plugin
> Bring your underwater world to life by letting fish breed naturally when players throw seeds into water! 🌊✨

![FishMating Logo](./logo256x256.png)

---

## 📦 Overview

**FishMating** is a feature-rich and highly configurable Minecraft plugin supporting **Minecraft 1.21.x through 26.1**. Crafted with ❤️ by `mrsuffix`, this plugin brings your aquatic biomes to life by introducing a unique, seed-based fish breeding mechanic.

Throw seeds into water to attract nearby fish! Fish will detect matching seeds within a **5-block radius**, swim toward them, and become *breeding-ready* after consuming a seed. If two ready fish find each other within **30 seconds**, they breed and spawn a cute baby fish 🐣. After breeding, fish wait **3 minutes** before breeding again.

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
# Version: 1.1.0

settings:
  detection-radius: 5.0
  breeding-timeout-seconds: 300
  breeding-cooldown-minutes: 3
  enable-particles: true
  particle-count: 5

fish-mappings:
  salmon: wheat_seeds
  cod: pumpkin_seeds
  pufferfish: melon_seeds
  tropical_fish: beetroot_seeds

advanced:
  debug-logging: false
  # Upper bound on how many fish are tracked at once (see note below)
  max-tracked-fish: 1000
  natural-growth: true
  breeding-success-rate: 1.0
````

Everything from detection range to particle effects and breeding logic can be tweaked! 🎛️

> **`max-tracked-fish`** caps how many fish the plugin tracks simultaneously. Fish are
> tracked as they spawn or their chunks load; once the cap is reached, additional fish
> are simply not tracked (they won't seek seeds or breed) until tracked fish are freed —
> e.g. when they die or their chunk unloads. This bounds memory and CPU on busy servers.
> Raise it for large aquatic servers, or lower it to be more conservative.

---

## 🛠 Installation

1️⃣ Download the latest **FishMating.jar** from the [Releases](https://github.com/YourUsername/FishMating/releases) tab.
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

---

## ⭐ Support the Project!

If you enjoy **FishMating**, leave a ⭐ on the repository!
Your support helps keep the project alive and encourages new updates! 🚀✨

---

## 🌊 Bring life to your oceans!

FishMating makes your aquatic world dynamic, fun, and interactive.
Perfect for survival servers, creative builds, and roleplay worlds alike.
Make your underwater biomes feel truly alive! 🐟❤️🌱
