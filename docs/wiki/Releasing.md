# Releasing

This page is for maintainers.

## Steps

1. Add the changelog for the new version, for example `docs/changelogs/0.1.1.md`.
2. Set the new version under `[mod]` in `mod.stonecutter.properties.toml`.
3. Commit and push. Every push runs a dry run and uploads a `release-preview` artifact. Open it and check the pages and files.
4. Create a tag with the same version, and push it:

```bash
git tag v0.1.1
git push origin v0.1.1
```

CI then publishes the release to GitHub, Modrinth and CurseForge. Each GitHub release has every jar, its sources jar, and a `SHA256SUMS.txt` file. Publishing needs the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets. If a project id is commented out in `mod.stonecutter.properties.toml`, that site is skipped.

## Updating the wiki

The pages in `docs/wiki/` are the source for this wiki. The GitHub wiki is a separate git repository, at `https://github.com/bichal/Velox.wiki.git`. After you change a page, copy the changed files into that repository, commit them, and push.
