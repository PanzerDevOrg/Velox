# Benchmarks

This page shows how long one server tick takes with many dropped items, with Velox and without it.

Each number is in milliseconds per tick. Lower is better. A server runs 20 ticks per second, so each tick has a budget of 50 ms. If a tick takes less than 50 ms, the server keeps up.

## Latest runs: 30 October 2026, three engines, three runs each

- A dedicated NeoForge server on a GitHub Actions runner (4 cores), Minecraft 1.21.1 (Java 21) and 26.1 (Java 25), a flat world, no players connected, no other mods.
- Diamond swords are thrown over a 112 × 112 block area. *Falling* is the first 80 ticks after the throw. *Resting* is 1,200 ticks later, with every item on the ground.
- Celeris `6adf83b` and Velox `main`. The same test ran three times in a row (CI runs 41010985422, 41010988292 and 41010991425 of `bichal/BichalBuildLogic`). Each cell is the median of the three runs.
- Celeris has three physics engines, and the test runs Velox on each one: **Java** (the pure Java kernel, needs no flag), **JNI** (the native kernel, the default, needs no flag) and **FFM** (the same native kernel through the Java foreign function API, which needs `--enable-preview` on Java 21). The server log of every run says which engine was loaded.

![Dropped items on a real server](https://raw.githubusercontent.com/bichal/Velox/main/docs/media/benchmarks/resting.png)

Each cell is falling / resting, in milliseconds per tick (lower is better, 50 ms is the budget):

| Minecraft | Items | Normal game | Velox, Java kernel | Velox, FFM kernel | Velox, JNI kernel |
|---|---|---|---|---|---|
| 1.21.1 | 2,000 | 15.30 / 8.50 | 5.42 / 1.40 | 3.32 / 1.50 | 2.96 / 1.30 |
| 1.21.1 | 8,000 | 104.34 / 54.85 | 11.57 / 8.76 | 11.70 / 8.85 | 10.62 / 8.66 |
| 1.21.1 | 20,000 | 492.99 / 260.40 | 28.53 / 21.54 | 31.15 / 22.94 | 28.00 / 21.29 |
| 26.1 | 2,000 | 14.70 / 9.01 | 3.47 / 2.23 | 2.65 / 2.32 | 2.45 / 2.15 |
| 26.1 | 8,000 | 96.73 / 57.71 | 17.72 / 9.71 | 15.06 / 9.64 | 15.86 / 9.31 |
| 26.1 | 20,000 | 488.27 / 260.24 | 39.87 / 29.65 | 36.45 / 30.74 | 36.52 / 27.39 |

What this shows:

- Velox is about 5 to 18 times faster than the normal game and keeps every row under the 50 ms budget. The normal game itself moves a lot between runs (for 20,000 falling items on 1.21.1 it took from 469 to 574 ms across these three runs, and 223 to 283 ms in three runs earlier the same day), so the exact factor is only good to about 20 percent.
- **The three engines tie.** At 8,000 and 20,000 items their results lie inside each other's ranges. At 2,000 items falling the Java kernel is slower (5.4 ms against 3.0 to 3.3 ms on 1.21.1), most likely because Java has to compile the kernel while the test runs; with 20,000 resting items it is level again.
- **The engine is not what makes Velox fast.** In the Java Flight Recorder profile of 1.21.1 the whole Celeris physics step takes 1.0 to 1.5 percent of the server thread with JNI, 1.6 to 2.0 percent with FFM and 7.0 to 7.9 percent with the Java kernel (the kernel alone is 5.6 to 6.1 percent). A native kernel can save at most that difference, which is inside the noise of these runs. The time goes on Minecraft's own work for each item, which Velox does not replace.
- Three runs earlier the same day (Celeris `6adf83b`, Java and JNI only: CI runs 41008058250, 41008080832 and 41008083510) agree: the two kernels lie inside each other's range in every row.

## Earlier run: 29 October 2026, on a CI runner

- A dedicated NeoForge 21.1.248 server on a GitHub Actions runner (4 cores), Minecraft 1.21.1, Java 21.
- A flat world, no players connected, no other mods.
- Diamond swords are thrown over a 112 × 112 block area. *Falling* is the first 80 ticks after the throw. *Resting* is 1,200 ticks later, with every item on the ground.
- One run per cell, with Celeris 3c1df34. It has no FFM column: that engine was added to the test on 30 October.

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
