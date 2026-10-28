# Requirements and files

| Minecraft | File |
|---|---|
| 1.21 - 1.21.6 | `velox-<version>+1.21.1.jar` |
| 1.21.7 - 1.21.10 | `velox-<version>+1.21.10.jar` |
| 1.21.11 | `velox-<version>+1.21.11.jar` |
| 26.1 - 26.3 | `velox-<version>+26.1.jar` |

Every version listed is checked against vanilla, tick by tick: each file runs a dedicated server on every version
it covers and must move every item exactly as vanilla does.

Velox works without any setup. It runs on Celeris's native physics kernel (AVX2, AVX-512 or NEON, picked for your
CPU) when it can, and on the pure-Java kernel otherwise:

| Minecraft (Java) | JVM arguments for the native kernel |
|---|---|
| 1.21.x (Java 21) | `--enable-preview` |
| 26.x (Java 25) | none |

Leave `--enable-native-access` out. It looks like the right flag for native code, but on Java 21 it makes the JVM
refuse native calls from every module it does not list, and NeoForge loads mods as named modules that the flag
(`ALL-UNNAMED`) cannot cover. The native kernel is then blocked and Celeris quietly uses its pure-Java kernel instead.
Nothing breaks and every item ends up in exactly the same place; only the native kernel's speed is lost, which in
our tests (100,000 items) made no measurable difference to the tick.
Velox logs which kernel it is using at startup, and what to change when it is not the native one.

[Back to Home](Home.md)
