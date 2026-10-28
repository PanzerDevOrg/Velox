# Building

Clone [BichalBuildLogic](https://github.com/bichal/BichalBuildLogic) next to Velox as `bichal-build-logic`.
Velox resolves Celeris from `mavenLocal()` first (to try an unreleased Celeris, `./gradlew publishToMavenLocal` in
it), then from Celeris's GitHub Pages Maven repository:

```bash
git clone https://github.com/bichal/BichalBuildLogic.git bichal-build-logic
cd Velox && ./gradlew buildAndCollect
```

[Back to Home](Home.md)
