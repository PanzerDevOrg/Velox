# Benchmarks

This page shows how long one server tick takes with many dropped items, with Velox and without it.

Each number is in milliseconds per tick. Lower is better. A server runs 20 ticks per second, so each tick has a budget of 50 ms. If a tick takes less than 50 ms, the server keeps up.

![Tick time with items resting on the ground](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/resting.png)

## The main results

| Situation | Items | Normal game | Velox | Faster by |
|---|---|---|---|---|
| Resting on the ground, spread out | 10,000 | 57 ms | 20 ms | 2.9 times |
| Resting, a medium crowd | 50,000 | 487 ms | 71 ms | 6.8 times |
| Resting, packed together | 100,000 | 3,315 ms | 171 ms | 19.4 times |
| Resting, one million items | 1,000,000 | 25,495 ms | 2,827 ms | 9.0 times |
| Falling from a height | 100,000 | 1,597 ms | 235 ms | 6.8 times |
| Sliding on ice | 50,000 | 1,022 ms | 99 ms | 10.3 times |

![Resting items by density](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/density.png)
![Falling, sliding and mergeable items](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/situations.png)

## What the numbers mean

- Velox is between 3 and 19 times faster. The more crowded the items are, the bigger the gain.
- Velox keeps about 25,000 to 35,000 items inside a 50 ms tick. The normal game keeps about 5,000 to 9,000 items when they are spread out, and fewer than 3,000 when they are piled up.
- Velox does not make a million items playable. A million items take 2.8 seconds per tick with Velox, and 25 seconds without it.

## How the tests were made

- Measured on 2026-10-22, with Velox 0.1.0 and Celeris 0.3.0.
- Minecraft 1.21.1 with NeoForge and Java 21.
- A shared 4 core cloud machine. Read differences under about 20 percent as a tie.
- A dedicated server on a flat world. No players were connected. Other mods were not part of the test.
- For these runs, `maxItemsPerLevel` was 1,048,576 and `windowSections` was 96. The defaults are 32,768 and 48.

The full tables and the method are in [docs/benchmarks.md](https://github.com/bichal/Velox/blob/main/docs/benchmarks.md) in the repository.

## Not tested yet

- Players connected.
- Minecraft 1.21.10, 1.21.11 and 26.x.
- Several worlds at once.
- Real farms with hoppers and pick-ups.
- Other mods.
- Faster processors.
