![Velox](./docs/media/banner.png)

<p align="center">
<img src="https://img.shields.io/badge/Dropped_items-all_in_one-0ea5e9?style=for-the-badge" alt="Dropped items worked out all in one">
<img src="https://img.shields.io/badge/Minecraft-1.21_--_26.3-3c8527?style=for-the-badge" alt="Minecraft 1.21 to 1.21.11 and 26.1 to 26.3">
<img src="https://img.shields.io/badge/NeoForge-Server_only-e8710a?style=for-the-badge" alt="NeoForge, server only">
<img src="https://img.shields.io/badge/Needs-Celeris-7c3aed?style=for-the-badge" alt="Needs Celeris">
</p>

<p align="center">
<!-- publish:only modrinth -->
<a href="https://modrinth.com/mod/ryy0AJVN"><img src="https://img.shields.io/badge/Download_on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white" alt="Download on Modrinth"></a>
<!-- /publish:only -->
<!-- publish:only curseforge -->
<a href="https://www.curseforge.com/projects/1729138"><img src="https://img.shields.io/badge/Download_on-CurseForge-f16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="Download on CurseForge"></a>
<!-- /publish:only -->
<a href="https://github.com/bichal/Velox/releases"><img src="https://img.shields.io/badge/Download_on-GitHub-24292f?style=for-the-badge&logo=github&logoColor=white" alt="Download on GitHub"></a>
</p>

> **Velox is built to take work off your server when lots of dropped items are lying around.** Big explosions, mob
> drops piling up, a storage room emptied onto the floor: instead of working out where each item goes one by one,
> Velox does them all in one go.

## ⚡ What it does

- 🧺 **Dropped items in one batch.** Items are worked out together instead of one by one, so a crowded floor costs the
  server far less time per tick.
- 👀 **Looks the same to players.** Items on the ground stay where they are and move the way they always did.
- 🌊 **Vanilla where it matters.** Items in water, lava, on hoppers or on slabs keep the normal game's behaviour.
- 🖥️ **Server only.** Players do not need to install anything.

Velox runs its physics on [Celeris](https://github.com/bichal/Celeris), which must be installed on the server too.

## 🏁 How fast

Milliseconds per server tick. The budget for 20 ticks per second is 50 ms.

- 🖥️ **20,000 falling items** (Minecraft 1.21.1, dedicated NeoForge server, CI runner, median of 3 runs on 30 Oct 2026):
  **28.0 ms instead of 493.0 ms**, 18× faster. Resting on the ground: **21.3 ms instead of 260.4 ms**, 12× faster.
  (The normal game moves a lot between runs, so read the factor as good to about 20 %.)
- 🧹 **8,000 resting items**: 8.7 ms instead of 54.9 ms, 6× faster. **2,000 resting items**: 1.3 ms instead of 8.5 ms, 7× faster.
- ⚙️ **Java, JNI and FFM kernels tie.** Velox runs on any of Celeris's three physics engines and they give the same tick
  to within noise (20,000 falling items: 28.5 ms Java, 28.0 ms JNI, 31.2 ms FFM; the whole physics step is 1–2 % of the
  server thread with a native kernel and about 7 % with the Java one). The gain over the normal game comes from Velox
  skipping Minecraft's per-item work, not from native code.
- 🧱 **Crowded piles gain most.** In the local tests, 100,000 items packed on 224 × 224 blocks: 171 ms instead of
  3,315 ms, 19× faster.
- 🏗️ **A million items**: 2.8 s per tick instead of 25.5 s. Velox does not make a million items playable, but it makes a
  big drop survivable.

![Dropped items on a real server](./docs/media/benchmarks/resting.png)
![Resting items at three densities](./docs/media/benchmarks/density.png)
![Other situations: sliding, mergeable and falling items](./docs/media/benchmarks/situations.png)

The **[benchmarks page](https://github.com/bichal/Velox/wiki/Benchmarks)** has every number, the test machine and how each
run was made. Results vary by about 20 % between runs on a shared machine.

## 📦 Requirements

- 🟩 **Minecraft:** 1.21 to 1.21.11 and 26.1 to 26.3.
- 🔶 **Loader:** NeoForge, on the server.
- 🧩 **Mod:** [Celeris](https://github.com/bichal/Celeris) 0.3.1.1 or newer, on the server.
- ☕ **Java:** 21 for 1.21.x, 25 for 26.x.

## 📚 Learn more

The **[Velox wiki](https://github.com/bichal/Velox/wiki)** has the settings, how it works, the benchmarks and the build
notes.

<p align="center">
<a href="https://github.com/bichal/Velox/wiki"><img src="https://img.shields.io/badge/Wiki-Guide_--_source-24292f?style=for-the-badge&logo=github&logoColor=white" alt="Wiki"></a>
<a href="https://github.com/bichal/Velox/issues"><img src="https://img.shields.io/badge/Found_a_bug-Report_it-d73a4a?style=for-the-badge&logo=github&logoColor=white" alt="Report a bug"></a>
<a href="https://github.com/bichal/Velox/tree/main/docs/changelogs"><img src="https://img.shields.io/badge/Whats_new-Changelog-2f81f7?style=for-the-badge&logo=github&logoColor=white" alt="Changelog"></a>
</p>

<!-- bichal:license -->
## License

| Content | License |
|---|---|
| Source code | [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html), see [`LICENSE-AGPL`](./LICENSE-AGPL) |
| Artwork, branding and documentation | [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/), see [`LICENSE-CC`](./LICENSE-CC) |

**Source code:** you may study, modify and redistribute it under the AGPL; if you run a modified version as a network service, its source must be available to that service's users.

**Artwork:** credit Bichal, no commercial use without permission, and share derivatives under the same license.

See [`LICENSE`](./LICENSE) for the full summary.
<!-- /bichal:license -->

<!-- publish:on -->

---

<!-- bichal:footer -->
Code: **AGPL-3.0** · Art: **CC BY-NC-SA 4.0** · Made by **Bichal**
<!-- /bichal:footer -->
