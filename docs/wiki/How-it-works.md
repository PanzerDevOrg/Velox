# How it works

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
items...) and compares every item, every tick, bit for bit. It also covers stacks that merge, items that despawn or are
removed, a chunk that unloads and loads again with its items, and more items than the batch holds. Each version is
claimed only after its check passes.

- **Handled by Velox:** items falling through the air or lying on full blocks such as stone, dirt, grass or ice.
- **Left to the normal game, while they are there:** items in water or lava, on hoppers, slabs, stairs or
  carpets, on slime, honey or soul sand, against fences or in cobwebs, or next to boats and minecarts.
- **How much it helps:** on a test server (Minecraft 1.21.1, 4 shared CPU cores) 50,000 items lying on the ground took
  about 70 ms per tick with Velox and 490 ms without; 500,000 took 1.1 s against 12 s, and 100,000 falling items
  235 ms against 1.6 s. The more crowded the items, the bigger the gain (3 times spread far apart, up to 19 times piled up).
  Velox does not remove everything Minecraft does per item: it keeps about 25,000 to 35,000 items inside a 50 ms tick,
  where the normal game manages roughly 5,000 to 9,000. Counts above the default `maxItemsPerLevel` (32,768) need
  that setting raised. Methods and tables in [`docs/benchmarks.md`](./docs/benchmarks.md).
- **Settings** are in `config/velox-common.toml`: `enabled = false` turns Velox off (after a restart), and
  `maxItemsPerLevel` (default 32,768) is how many items per dimension it handles.
- **Minecraft** 1.21 - 1.21.11 and 26.1 - 26.3, on NeoForge. Download the file made for your version.

<p align="center">
<a href="https://github.com/bichal/Celeris"><img src="https://img.shields.io/badge/Needs-Celeris-7c3aed?style=for-the-badge" alt="Requires Celeris"></a>
</p>

[Back to Home](Home.md)
