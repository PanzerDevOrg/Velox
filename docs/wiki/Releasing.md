# Releasing

This README is also the description on Modrinth and CurseForge (everything outside `publish:off` blocks). Add
`docs/changelogs/<version>.md`, set `version` under `[mod]`, then tag `v<version>`. CI publishes through
bichal-build-logic's shared publisher; every other push uploads a `release-preview` artifact with the pages and files
as they would be published. Every release on GitHub carries each jar with its `-sources.jar` (the AGPL source for
that file) and `SHA256SUMS.txt`.

`modrinth_id` and `curseforge_id` under `[publish]` in `mod.stonecutter.properties.toml` are Velox's project ids: a tag
publishes to GitHub, Modrinth and CurseForge, with `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` as secrets (the same ones the
other mods use). Comment an id out to skip that site; on a manual CI run on the tag (dry run off), `targets` picks the sites.

[Back to Home](Home.md)
