# Requirements and files

## What you need

- A NeoForge server for Minecraft 1.21 to 1.21.11, or 26.1 to 26.3.
- [Celeris](https://github.com/bichal/Celeris) 0.3.1.1 or newer, on the same server. Velox does not work without it.
- Java 21 for Minecraft 1.21.x. Java 25 for Minecraft 26.x.

Players do not need to install Velox.

## Pick your file

| Minecraft version | File |
|---|---|
| 1.21 to 1.21.6 | `velox-0.1.0+1.21.1.jar` |
| 1.21.7 to 1.21.10 | `velox-0.1.0+1.21.10.jar` |
| 1.21.11 | `velox-0.1.0+1.21.11.jar` |
| 26.1 to 26.3 | `velox-0.1.0+26.1.jar` |

**Example:** Your server runs Minecraft 1.21.5. That is in the first row, so use `velox-0.1.0+1.21.1.jar`.

Every file was checked against vanilla on each version in its row. A test server ran Velox and vanilla side by side, and every item had to move exactly as it does in vanilla.

## Java flags

Velox works without flags. It uses the native physics kernel of Celeris when it can, because that kernel is faster.

| Minecraft | Java | Flag |
|---|---|---|
| 1.21.x | 21 | `--enable-preview` |
| 26.x | 25 | None |

With the current Celeris release, Java 21 needs `--enable-preview` for the native kernel. Without it, Velox still works and uses the Java kernel. In our tests with 100,000 items, this made no measurable difference to the tick time. For the details, see the note in the [Celeris JVM flags page](https://github.com/bichal/Celeris/wiki/JVM-flags).

Do not add `--enable-native-access`. On Java 21 it blocks the native kernel for NeoForge mods.

To see which kernel is running, search the log for `Velox: items simulated by Celeris`. The text in the brackets tells you. See [Troubleshooting](Troubleshooting.md).
