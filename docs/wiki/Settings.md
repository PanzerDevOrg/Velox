# Settings

Velox reads one file. Change a value, save the file, and restart the server.

- On Minecraft 1.21.x, the file is `config/velox-common.toml`.
- On newer NeoForge versions (26.3 and later), the file is `config/velox-local.toml`.

The file has a comment above each option. On the first start, Velox writes it with the default values.

## The four options

| Option | Default | Allowed values | What it does |
|---|---|---|---|
| `enabled` | `true` | `true` or `false` | Turns Velox on or off. Velox reads this when the server starts, so restart after you change it. |
| `maxItemsPerLevel` | `32768` | 1024 to 1048576 | The most items per world (dimension) that Velox handles. Items above this number keep the normal game physics. |
| `windowSections` | `48` | 8 to 256 | How wide an area of terrain, around the items, Velox copies. Sections are 16 blocks wide, so 48 covers 768 blocks. Items outside the area keep the normal game physics. The area follows the main group of items. |
| `sectionFillsPerTick` | `256` | 1 to 4096 | How many terrain sections Velox copies each tick. A higher number fills the area faster. |

## Examples

Change one value at a time, and check the server after each change.

### Turn Velox off

```toml
enabled = false
```

Restart the server. All items then use the normal game physics.

### A world with more than 32,768 items

```toml
maxItemsPerLevel = 131072
```

Items above the limit keep the normal game physics. This raises the limit to 131,072 items per world.

### Items spread over a wide area

```toml
windowSections = 96
```

Items outside the copied area keep the normal game physics. A wider area helps when the items are spread out. The Velox benchmarks used 96 for their wide areas.

### Items that drop into a freshly loaded area

```toml
sectionFillsPerTick = 1024
```

Items join Velox sooner, because the area fills faster. In our tests with 100,000 falling items, 1024 was the fastest of the values we tried.

## Which option when

| What you see | Try this |
|---|---|
| Velox is not doing anything | Check `enabled`, then read [Troubleshooting](Troubleshooting). |
| Many items, but some stay on normal physics | Raise `maxItemsPerLevel`. |
| Items spread over a big area use normal physics | Raise `windowSections`. |
| Items take a while to join Velox after they fall | Raise `sectionFillsPerTick`. |
