# Auto Spawner Mob Attack

<img src="icon.png" alt="Auto Spawner Mob Attack icon" width="128" align="right">

A client-side Fabric mod for spawner farms. It stands where you put it, hits the mobs you pick, eats when hungry and picks up drops.

> **Use it only on servers that allow it.** Automatic attacking breaks the rules on many servers. Ask the server staff first.

## What it does
- **F10** turns it on or off, **K** opens the menu (both keys can be changed in Controls).
- Walks to and attacks the mobs you selected (default: Blaze). It never attacks players and never breaks or places blocks.
- Picks the best sword in your hotbar by itself (netherite > diamond > iron > stone > golden > wooden).
- Eats any selected food found in the hotbar when your hunger is at or below the level you set. Golden apples are listed but off by default.
- Picks up nearby drops when no mob is around.
- Shows a small info panel (HUD) with running time, hits, kills per mob and collected items. You can move it to any corner or turn it off.
- Turns itself off when health is low, the sword is about to break, the inventory is full or the food runs out. A warning shows above the hotbar.

## Menu
- **Mobs:** search box, spawn egg icons, on/off switches. Spawner mobs are listed first.
- **Foods:** search box, on/off switches. Common foods are listed first, bad foods are hidden. All foods start selected.
- **Settings:** sliders for search range, attack distance, attack interval, hunger level and health stop, the item pickup switch, the safety stops and the HUD options.
- **Status:** cards with state, hits, kills, running time, food and sword durability, on/off button, per-mob kill table and collected-item table with per-hour rates, and a reset button.

Kills are counted when a mob you hit reaches 0 health. Items are counted when the item count in your inventory goes up while the mod is on, so items you pick up by hand are counted too. Statistics reset when you restart the game.

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

## FAQ
**Is this allowed on servers?** Only if the server staff allow it. Many servers ban automatic attacking. Singleplayer is always fine.

**Does it work with other mods?** It only needs Fabric API. It is not tested with other automation mods.

**The mod turned itself off.** Look above the hotbar: it says why (low health, sword about to break, full inventory or no food).

**Where are my settings?** `config/auto_spawner_mob_attack.json`. Delete it to reset everything.

## Build
Every push builds all versions with GitHub Actions. The jars are published in a release named after `mod_version` (for example `v1.1.0`).
To build one version yourself, open its folder (the root folder is 1.21.4) and run `gradle build` (Java 17 for 1.20.4, Java 21 for 1.20.6-1.21.11, Java 25 for 26.x).
