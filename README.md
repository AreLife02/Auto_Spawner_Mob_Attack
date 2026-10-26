# Auto Spawner Mob Attack

A client-side Fabric mod for spawner farms. It stands where you put it, hits the mobs you pick, eats when hungry and picks up drops.

> **Use it only on servers that allow it.** Automatic attacking breaks the rules on many servers. Ask the server staff first.

## What it does
- **F10** turns it on or off, **K** opens the menu (both keys can be changed in Controls).
- Walks to and attacks the mobs you selected (default: Blaze). It never attacks players and never breaks or places blocks.
- Finds your sword in the hotbar by itself.
- Eats any food you selected when your hunger is at or below the level you set.
- Picks up nearby drops when no mob is around.
- Turns itself off when health is low, the sword is about to break, the inventory is full or the food runs out. A warning shows above the hotbar.

## Menu
- **Mobs:** search box, spawn egg icons, on/off switches. Spawner mobs are listed first.
- **Foods:** search box, on/off switches. Common foods are listed first, bad foods are hidden. All foods start selected.
- **Settings:** search range, attack distance, attack interval, hunger bar, item pickup and the safety stops.
- **Status:** on/off button, hit counter, food left.

Settings are saved to `config/auto_spawner_mob_attack.json`.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version.
2. Download the matching jar from the [Releases](../../releases/latest) page and put it in your `mods` folder.

| Jar | Works on |
| --- | --- |
| `mc1.20.4` | 1.20.4 |
| `mc1.20.6` | 1.20.5, 1.20.6 |
| `mc1.21.1` | 1.21, 1.21.1 |
| `mc1.21.3` | 1.21.2, 1.21.3 |
| `mc1.21.4` | 1.21.4 |
| `mc1.21.5` | 1.21.5 |
| `mc1.21.8` | 1.21.6, 1.21.7, 1.21.8 |
| `mc1.21.10` | 1.21.9, 1.21.10 |
| `mc1.21.11` | 1.21.11 |
| `mc26.1.2` | 26.1, 26.1.1, 26.1.2 |
| `mc26.2` | 26.2 |
| `mc26.3` | 26.3 |
| `mc26.4` | 26.4 |

## Build
Every push builds all versions with GitHub Actions and updates the **Latest build** release.
To build one version yourself, open its folder (the root folder is 1.21.4) and run `gradle build` (Java 21, or Java 25 for 26.x).
