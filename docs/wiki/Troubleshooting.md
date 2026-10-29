# Troubleshooting

Start with the log. Velox writes lines when it starts, and each line says what it is doing. Search the log for lines that start with `Velox`.

## What the log lines mean

| Log line | What it means | What to do |
|---|---|---|
| `Velox: disabled in config -- items use vanilla physics` | `enabled` is `false`. | Set `enabled = true`, and restart the server. |
| `Velox: Celeris physics engine unavailable (why) -- items use vanilla physics` | Celeris could not start its physics engine. The text in the brackets says why. | Read the Celeris lines in the log. See the [Celeris troubleshooting page](https://github.com/bichal/Celeris/wiki/Troubleshooting). |
| `Velox: items simulated by Celeris (java)` | Velox works, with the Java kernel. On Java 21 the line also says what to add. | Add `--enable-preview` on Java 21 for the native kernel. See [Requirements and files](Requirements-and-files). |
| `Velox: items simulated by Celeris (native-jni-avx2)` | Velox works, with the native kernel. The name in the brackets can differ on your computer. | Nothing. This is the fastest setup. |
| `Velox: item physics for minecraft:overworld on java (capacity 32768)` | Velox started handling that world. The capacity is the most items it handles there. | Nothing. |

## Common problems

### Some items act like vanilla

This is expected for some items. Velox leaves these to the normal game: items in water or lava, on hoppers, slabs, stairs or carpets, and more. See [How it works](How-it-works).

Items above `maxItemsPerLevel`, and items outside `windowSections`, also keep the normal game physics. You can raise these in [Settings](Settings).

### I changed a setting, and nothing changed

Velox reads its settings when the server starts. Restart the server after you change them.

### The server still lags with many items

Velox removes most of the physics work, but Minecraft still does other work for each item. Past about 25,000 to 35,000 items, even Velox cannot keep 20 ticks per second. Fewer items always helps most. See [Benchmarks](Benchmarks) for the numbers.

### My Minecraft version is not in the list

Velox is only supported on the versions listed in [Requirements and files](Requirements-and-files). Other versions are not tested, so they are not supported.

### Velox says Celeris is missing

Velox needs Celeris 0.3.1.1 or newer, on the server. Install it, then restart the server.

## Frequently asked questions

**Do players need Velox?**
No. Velox runs on the server only.

**Does Velox change how items behave?**
No. Items end up in the same places as in the normal game. Velox only changes how fast the work is done.

**Does Velox affect items from other mods?**
Only plain item entities are handled. Items from other mods that are a different kind of entity are left alone.

**Where do I report a bug?**
On the [issues page](https://github.com/bichal/Velox/issues). Include the Velox lines from the log.
