@file:Suppress("AvoidDuplicateDependencies")

import com.panzer.gradle.PanzerModExtension

plugins {
    id("panzer.neoforge-mod")
    idea
}

// Celeris (dependency and Maven repositories), neoforge.mods.toml expansion,
// legal files and buildAndCollect come from panzer.neoforge-mod:
// [depends_on.celeris] and [legal] in the TOML.

val modProps = extensions.getByType(PanzerModExtension::class.java).props
val mainSourceSet = sourceSets.main.get()
val runDir = rootProject.file("versions/${modProps.currentVersion}/run")
val parityDir = rootProject.file("versions/${modProps.currentVersion}/run-parity")

neoForge {
    runs {
        all {
            sourceSet = mainSourceSet
        }

        register("client") {
            client()
            gameDirectory = runDir
            systemProperty("neoforge.enabledGameTestNamespaces", modProps.modId)
        }

        register("server") {
            server()
            gameDirectory = runDir
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modProps.modId)
        }

        // Velox against vanilla, bit for bit, on a headless server: ParityHarness.
        register("parity") {
            server()
            gameDirectory = parityDir
            programArgument("--nogui")
            systemProperty("velox.parity", "true")
        }
    }
}

listOf(runDir, parityDir).forEach { if (!it.exists()) it.mkdirs() }

// A fresh flat world for every check. The EULA is accepted for this throwaway
// test server only; each Minecraft version gets its own port (they may run in parallel).
val parityPort = 25600 + (modProps.currentVersion.hashCode() and 0x1ff)
val prepareParity = tasks.register("prepareParity") {
    val dir = parityDir
    val port = parityPort
    doLast {
        dir.resolve("world").deleteRecursively()
        dir.resolve("velox-parity.txt").delete()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText(
            listOf(
                "level-type=minecraft\\:flat",
                "generate-structures=false",
                "spawn-monsters=false",
                "online-mode=false",
                "server-port=$port",
                "max-tick-time=-1",
                "view-distance=4",
                "simulation-distance=4",
            ).joinToString("\n", postfix = "\n")
        )
    }
}

tasks.matching { it.name == "runParity" }.configureEach { dependsOn(prepareParity) }

tasks.register("parity") {
    group = "verification"
    description = "Runs Velox and vanilla side by side on a headless server and compares every item, every tick."
    dependsOn("runParity")
    val report = parityDir.resolve("velox-parity.txt")
    val serverLog = parityDir.resolve("logs/latest.log")
    doLast {
        if (!report.isFile) {
            throw GradleException("Velox parity: no report in $report (the server stopped before the check ended)")
        }
        val text = report.readText()
        println(text)
        if (!text.lineSequence().first().endsWith("PASS")) {
            throw GradleException("Velox parity failed: see $report")
        }
        // With the right JVM flags players get Celeris's native kernel, so CI checks
        // that one: a fall back to the Java kernel (JVM flags, a library that did not
        // load) must not pass unnoticed. Locally it is only reported.
        val kernelLine = Regex("""Velox: items simulated by Celeris \(([^)]+)\).*""")
        val kernel = if (serverLog.isFile) serverLog.useLines { lines -> lines.firstNotNullOfOrNull { kernelLine.find(it) } } else null
        println("Kernel: ${kernel?.value ?: "not logged"}")
        if (System.getenv("CI") == "true" && kernel?.groupValues?.get(1)?.startsWith("native") != true) {
            throw GradleException("Velox parity ran without Celeris's native kernel: see $serverLog")
        }
    }
}
