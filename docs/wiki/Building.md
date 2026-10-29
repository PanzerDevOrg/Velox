# Building

This page is for people who want to build Velox from source.

## What you need

- A JDK 25 to run Gradle.
- The shared build logic, in a folder named `bichal-build-logic`, next to the Velox folder.

## Steps

```bash
git clone https://github.com/bichal/BichalBuildLogic.git bichal-build-logic
git clone https://github.com/bichal/Velox.git
cd Velox
./gradlew buildAndCollect
```

The jars are written to `build/libs/`, in a folder named after the version.

## Other useful tasks

- `./gradlew build` builds the mod and runs the tests.
- `./gradlew parity` runs the check against vanilla that CI runs. It starts a headless server, and compares every item on every tick. Run it after you change the physics code.

## Use a local copy of Celeris

Velox looks for Celeris in your local Maven folder first. To try a Celeris build you made yourself, run `./gradlew publishToMavenLocal` in the Celeris folder. Velox then uses that copy.

Back to [Home](Home.md).
