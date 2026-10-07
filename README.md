![Velox](./docs/media/banner.png)

<p align="center">
<img src="https://img.shields.io/badge/Dropped%20items-all%20in%20one-0ea5e9?style=for-the-badge" alt="Dropped items worked out all in one">
<img src="https://img.shields.io/badge/Minecraft-1.21%20--%2026.3-3c8527?style=for-the-badge" alt="Minecraft 1.21 – 1.21.11 and 26.1 – 26.3">
<img src="https://img.shields.io/badge/NeoForge-Server%20only-e8710a?style=for-the-badge" alt="NeoForge, server only">
</p>

> **Velox is built to take work off your server when lots of dropped items are lying around.** Big explosions, mob
> drops piling up, a storage room emptied onto the floor: instead of working out where each item goes one by one,
> Velox does them all in one go.

## ✨ Features

- 🎯 **Just like vanilla:** items fall, slide and get picked up exactly as in the normal game. Farms and
  contraptions keep working.
- 🖥️ **Server only:** players join with a normal game, nothing to install on their side. Also works in
  singleplayer.
- 🔌 **Nothing to set up:** add it and it works.
- 🔬 **Checked on every version:** each supported Minecraft version is tested tick by tick against vanilla, and every
  item has to end up in exactly the same spot.

> **You also need [Celeris](https://github.com/PanzerDevOrg/Celeris).** Put both in the server's `mods` folder
> (or in your own, for singleplayer).

## 💡 Good to know

- ✅ **Handled by Velox:** items falling through the air or lying on full blocks such as stone, dirt, grass or ice.
- ↩️ **Left to the normal game, while they are there:** items in water or lava, on hoppers, slabs, stairs or
  carpets, on slime, honey or soul sand, against fences or in cobwebs, or next to boats and minecarts.
- ⚙️ **Settings** are in `config/velox-common.toml`: `enabled = false` turns Velox off (after a restart), and
  `maxItemsPerLevel` (default 32,768) is how many items per dimension it handles.
- 📦 **Minecraft** 1.21 – 1.21.11 and 26.1 – 26.3, on NeoForge. Download the file made for your version.

<p align="center">
<a href="https://github.com/PanzerDevOrg/Celeris"><img src="https://img.shields.io/badge/Needs-Celeris-7c3aed?style=for-the-badge" alt="Requires Celeris"></a>
</p>

<!-- publish:off -->

## Files per version

| Minecraft | File |
|---|---|
| 1.21 – 1.21.6 | `velox-<version>+1.21.1.jar` |
| 1.21.7 – 1.21.10 | `velox-<version>+1.21.10.jar` |
| 1.21.11 | `velox-<version>+1.21.11.jar` |
| 26.1 – 26.3 | `velox-<version>+26.1.jar` |

Every version listed is checked against vanilla, tick by tick: each file runs a dedicated server on every version
it covers and must move every item exactly as vanilla does.

## JVM flags (optional)

Velox works without any setup. It runs on Celeris's native physics kernel (AVX2, AVX-512 or NEON, picked for your
CPU) when it can, and on the pure-Java kernel otherwise:

| Minecraft (Java) | JVM arguments for the native kernel |
|---|---|
| 1.21.x (Java 21) | `--enable-preview` |
| 26.x (Java 25) | none |

Leave `--enable-native-access` out: on Java 21 it switches the native kernel off, because NeoForge loads mods as
named modules that the flag cannot cover. Velox logs which kernel it is using at startup, and what to change when it
is not the native one.

## Configuration

`config/velox-common.toml`:

| Option | Default | Effect |
|---|---|---|
| `enabled` | `true` | Turn the batch physics on or off |
| `maxItemsPerLevel` | `32768` | Items simulated per dimension; the rest keep vanilla physics |
| `windowSections` | `48` | Width of the terrain snapshot around the items, in 16-block sections |
| `sectionFillsPerTick` | `64` | Chunk sections copied into the snapshot per tick |

## How it works

Once per tick, right before the level ticks its entities, every item in the dimension is stepped in one Celeris
batch. Each item then applies its result at the exact point of its own tick where vanilla would have run
`applyGravity()` / `move()` / friction; the rest of `ItemEntity.tick` runs unchanged. Velox contains no simulation
code of its own: it only connects Minecraft to Celeris.

| Piece | Role |
|---|---|
| `ServerLevelMixin` | Runs the batch right before the level ticks its entities (after block and fluid ticks) |
| `ItemEntityMixin` | Replaces the physics block of `ItemEntity.tick` with the batch result, only when it was simulated |
| `EntityMixin` | Notices outside writes (teleports, knockback, commands) and re-reads those items next tick |
| `LevelChunkMixin` | Keeps the terrain snapshot current on every block change |
| `ItemPhysicsWorld` | Per-dimension Celeris batch, terrain window and bridge |
| `BlockClassifier` | Block state to air / full cube (by friction) / complex |
| `TerrainSampler` | Copies loaded chunk sections into the snapshot, never loads chunks |

Anything the engine does not model exactly (fluids, partial or special blocks, boats/shulkers/minecarts, the world
border, unloaded chunks) makes that item run vanilla code for that tick. Only plain `ItemEntity` instances with
normal gravity are simulated; subclasses from other mods are left alone.

Every supported version is checked in CI by `./gradlew parity`: a headless server runs Velox and vanilla side by side
on the same course (ice, slime, soul sand, honey, slabs, fences, cobwebs, walls placed and floors removed under
items...) and compares every item, every tick, bit for bit. Each version is claimed only after its check passes.

## Building

Clone [PanzerBuildLogic](https://github.com/PanzerDevOrg/PanzerBuildLogic) next to Velox as `panzer-build-logic`.
Velox resolves Celeris from `mavenLocal()` first (to try an unreleased Celeris, `./gradlew publishToMavenLocal` in
it), then from Celeris's GitHub Pages Maven repository:

```bash
git clone https://github.com/PanzerDevOrg/PanzerBuildLogic.git panzer-build-logic
cd Velox && ./gradlew buildAndCollect
```

## Releasing

This README is also the description on Modrinth and CurseForge (everything outside `publish:off` blocks). Add
`docs/changelogs/<version>.md`, set `version` under `[mod]`, then tag `v<version>`. CI publishes through
panzer-build-logic's shared publisher; every other push uploads a `release-preview` artifact with the pages and files
as they would be published. Modrinth and CurseForge also need `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` as secrets of
this repository (a private repository does not get the organization's); without them a release goes to GitHub only,
and running the CI on the tag later (dry run off, `targets` = the site) finishes it.

<!-- panzer:license -->
## License

| Content | License |
|---|---|
| Source code | [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html), see [`LICENSE-AGPL`](./LICENSE-AGPL) |
| Artwork, branding and documentation | [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/), see [`LICENSE-CC`](./LICENSE-CC) |

**Source code:** you may study, modify and redistribute it under the AGPL; if you run a modified version as a network service, its source must be available to that service's users.

**Artwork:** credit Panzer, no commercial use without permission, and share derivatives under the same license.

See [`LICENSE`](./LICENSE) for the full summary.
<!-- /panzer:license -->

<!-- publish:on -->

---

<!-- panzer:footer -->
Code: **AGPL-3.0** · Art: **CC BY-NC-SA 4.0** · Made by **Panzer**
<!-- /panzer:footer -->
