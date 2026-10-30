# Benchmarks

This page shows how long one server tick takes with many dropped items, with Velox and without it.

Each number is in milliseconds per tick. Lower is better. A server runs 20 ticks per second, so each tick has a budget of 50 ms. If a tick takes less than 50 ms, the server keeps up.

## Latest run: 29 October 2026, on a CI runner

- A dedicated NeoForge 21.1.248 server on a GitHub Actions runner (4 cores), Minecraft 1.21.1, Java 21.
- A flat world, no players connected, no other mods.
- Diamond swords are thrown over a 112 × 112 block area. *Falling* is the first 80 ticks after the throw. *Resting* is 1,200 ticks later, with every item on the ground.
- One run per cell, with Celeris 3c1df34 (the current `main`).

![Dropped items on a real server](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/resting.png)

| Items | Phase | Normal game | Velox, Java kernel | Velox, native kernel | Velox (native kernel) faster than the normal game by |
|---|---|---|---|---|---|
| 2,000 | Falling | 11.46 ms | 4.36 ms | 2.59 ms | 4.4 times |
| 2,000 | Resting | 7.55 ms | 1.34 ms | 1.20 ms | 6.3 times |
| 8,000 | Falling | 97.71 ms | 11.34 ms | 9.02 ms | 10.8 times |
| 8,000 | Resting | 53.72 ms | 8.98 ms | 7.46 ms | 7.2 times |
| 20,000 | Falling | 508.50 ms | 30.28 ms | 31.38 ms | 16.2 times |
| 20,000 | Resting | 246.59 ms | 22.12 ms | 21.62 ms | 11.4 times |

Velox keeps every one of these under the 50 ms budget, and the normal game does not keep 20,000 items under it at all.

### Two Celeris versions, same test

Velox uses Celeris for its physics. The same test was run with the native kernel on Celeris 0.3.1.1 (the release) and on Celeris 3c1df34 (the current `main`).

| Items | Phase | Celeris 0.3.1.1 | Celeris 3c1df34 |
|---|---|---|---|
| 2,000 | Falling | 2.84 ms | 2.59 ms |
| 2,000 | Resting | 1.26 ms | 1.20 ms |
| 8,000 | Falling | 10.82 ms | 9.02 ms |
| 8,000 | Resting | 5.87 ms | 7.46 ms |
| 20,000 | Falling | 26.63 ms | 31.38 ms |
| 20,000 | Resting | 18.96 ms | 21.62 ms |

These two runs are within the run-to-run noise. The normal game also changed between the two runs (508.50 ms against 425.95 ms for 20,000 falling items), so the measurements vary by about 20 percent. On this test, the newer Celeris is **not measurably faster**, and in the 20,000-item rows it is slightly slower. Any gain in the Celeris benchmarks does not show up here as a change in Velox's tick time.

### Run again on 30 October 2026, three runs

The same test was run three times in a row, with Celeris at `6adf83b` (the changes to the physics kernel after 3c1df34) and Velox `main` (CI runs 41008058250, 41008080832 and 41008083510 of `bichal/BichalBuildLogic`, Minecraft 1.21.1). Each cell is the median of the three runs, with the lowest and highest in brackets.

| Items | Phase | Normal game | Velox, Java kernel | Velox, native kernel |
|---|---|---|---|---|
| 2,000 | Falling | 7.99 ms (6.69–8.31) | 1.98 ms (1.38–3.08) | 1.61 ms (1.38–1.75) |
| 2,000 | Resting | 6.13 ms (4.84–6.27) | 0.94 ms (0.87–1.08) | 0.87 ms (0.66–1.04) |
| 8,000 | Falling | 59.17 ms (49.05–63.82) | 7.66 ms (4.80–8.95) | 7.13 ms (6.44–8.29) |
| 8,000 | Resting | 41.47 ms (31.19–44.14) | 4.41 ms (3.97–5.09) | 5.05 ms (4.57–6.17) |
| 20,000 | Falling | 278.02 ms (222.88–282.78) | 18.93 ms (18.64–25.54) | 21.28 ms (21.06–27.37) |
| 20,000 | Resting | 199.27 ms (145.42–207.53) | 15.22 ms (14.84–18.90) | 15.48 ms (14.65–19.78) |

What this shows:

- Velox is about 5 to 15 times faster than the normal game, the same range as the run of 29 October. The normal game itself moves a lot between runs (145 to 247 ms for 20,000 resting items over the four runs), so the exact factor is only good to about 20 percent.
- **The native kernel is not what makes Velox fast.** The two kernels are inside each other's range in every row. With 20,000 falling items the native kernel was 7 to 13 percent slower than the Java kernel in all three runs (21.06, 27.37, 21.28 ms against 18.64, 25.54, 18.93 ms), which is a small difference, but it is not an advantage.
- In the Java Flight Recorder profile of the same runs the native kernel takes 0.8 to 1.4 percent of the server thread and the Java kernel takes 4.8 to 5.0 percent. Even the Java kernel is a small part of the tick, so a faster kernel cannot make much difference. The time goes on Minecraft's own work for each item.
- The column in the table above that says "Velox (native kernel) faster than the normal game by" compares Velox with the normal game. It does not compare the native kernel with the Java kernel.

## Earlier measurements: 22 October 2026, local harness

These runs used a separate test program, not the CI runner. They include larger item counts, which the CI test does not reach.

- Velox 0.1.0 with Celeris 0.3.0, Minecraft 1.21.1 with NeoForge and Java 21.
- A shared 4 core cloud machine.
- For these runs, `maxItemsPerLevel` was 1,048,576 and `windowSections` was 96. The defaults are 32,768 and 48.

![Resting items at three densities](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/density.png)

Density is items per block of floor. The same number of items costs more when they are piled up, and Velox saves the most there.

| Items | Density | Normal game | Velox | Faster by |
|---|---|---|---|---|
| 10,000 | 80 × 80 (1.56 per block) | 189 ms | 13 ms | 14.5 times |
| 50,000 | 160 × 160 (1.95 per block) | 1,195 ms | 96 ms | 12.4 times |
| 100,000 | 224 × 224 (1.99 per block) | 3,315 ms | 171 ms | 19.4 times |
| 100,000 | 448 × 448 (0.50 per block) | 1,230 ms | 184 ms | 6.7 times |
| 1,000,000 | 1424 × 1424 (0.49 per block) | 25,495 ms | 2,827 ms | 9.0 times |

![Other situations: sliding, mergeable and falling items](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/situations.png)

| Situation | Items | Normal game | Velox | Faster by |
|---|---|---|---|---|
| Resting, mergeable | 50,000 | 482 ms | 65 ms | 7.4 times |
| Sliding on ice | 50,000 | 1,022 ms | 99 ms | 10.3 times |
| Falling from a height | 100,000 | 1,597 ms | 235 ms | 6.8 times |

Velox does not make a million items playable. A million items take 2.8 seconds per tick with Velox, and 25 seconds without it.

The full tables and the method are in [docs/benchmarks.md](https://github.com/bichal/Velox/blob/main/docs/benchmarks.md) in the repository.

## Death ticks: not measured yet

We want to know how much the loot of mobs that die together costs, with and without the drops. The first attempt did not measure it: every case took between 0.1 and 0.25 ms, which means the timed tick did not include the deaths. Those numbers are not published here. The measurement will be fixed before it is reported.

## What the numbers mean

- Velox is much faster where the items are crowded, and the gain grows with the number of items.
- Velox keeps about 25,000 to 35,000 items inside a 50 ms tick. The normal game keeps about 5,000 to 9,000 items when they are spread out, and fewer than 3,000 when they are piled up.
- Velox does not make a million items playable. It makes a big drop survivable.

## Not tested yet

- Players connected.
- Minecraft 1.21.10, 1.21.11 and 26.x in the CI test.
- Several worlds at once.
- Real farms with hoppers and pick-ups.
- Other mods.
- Mob deaths with their drops (see above).
- Faster processors.
