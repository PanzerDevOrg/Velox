# How it works

Velox moves dropped items in one batch instead of one by one.

## The short version

Once per tick, before the world moves its entities, Velox works out where all the dropped items go. It uses Celeris to do this. Each item then uses that result at the same point in its tick where the normal game would have moved it.

The rest of each item's tick runs as usual. Pickup delay, merging, age and despawning all work the same way.

Players see the same items in the same places. Velox changes how fast the work is done, not what the items do.

## What Velox handles

Velox handles items that fall through the air, and items that lie on full solid blocks such as stone, dirt, grass or ice.

## What the normal game still handles

Velox leaves these items to the normal game code while they are in these places:

- In water or lava.
- On hoppers, slabs, stairs or carpets.
- On slime, honey or soul sand.
- Against fences, or in cobwebs.
- Next to boats or minecarts.
- Near the world border, or in chunks that are not loaded.

Velox checks this every tick, so an item can move between the two groups as it moves.

Only plain item entities with normal gravity are handled. Items from other mods that are a different kind of entity are left alone.

## How it is tested

Every supported version is checked in CI. A headless server runs Velox and vanilla side by side on the same course. The course has ice, slime, soul sand, honey, slabs, fences, cobwebs, walls placed and floors removed under the items. The check compares every item on every tick.

The check also covers stacks that merge, items that despawn, chunks that unload and load again with their items, and more items than the batch can hold. A version is only listed as supported after this check passes.

## Limits

Velox does not remove all of the work that Minecraft does for each item. The numbers and the limits are on the [Benchmarks](Benchmarks.md) page.

## For developers

| Part | What it does |
|---|---|
| `ServerLevelMixin` | Runs the batch before the world ticks its entities. |
| `ItemEntityMixin` | Replaces the physics part of an item's tick with the batch result, when that item was simulated. |
| `EntityMixin` | Notices changes made from outside, such as teleports, and reads those items again on the next tick. |
| `LevelChunkMixin` | Keeps the terrain copy up to date when blocks change. |
| `ItemPhysicsWorld` | One Celeris batch and terrain window for each world. |
| `BlockClassifier` | Sorts a block into air, a full cube, or a complex block. |
| `TerrainSampler` | Copies loaded terrain into the window. It never loads chunks. |

Velox has no physics of its own. It connects Minecraft to Celeris.
