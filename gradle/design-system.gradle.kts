import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity

data class OrbitDesignLock(val version: String, val files: Map<String, String>)
data class OrbitDesignTools(val node: File, val npm: File, val childPath: String)

fun orbitExecutable(name: String, override: String?, path: String, workingDirectory: File): File {
    val requested = override ?: name
    val property = if (name == "node") "designSystemNode" else "designSystemNpm"
    fun executable(file: File): File? = file.absoluteFile.normalize().takeIf { it.isFile && it.canExecute() }
    fun failure(): Nothing = throw GradleException(
        "Cannot find executable design-system $name '$requested'. " +
            "Set -P$property=/absolute/path/to/$name (Node >=22 and npm required).",
    )
    if (requested.isBlank()) failure()
    if (File(requested).isAbsolute || requested.contains('/') || requested.contains('\\')) {
        return executable(workingDirectory.resolve(requested)) ?: failure()
    }
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val names = if (windows && File(requested).extension.isEmpty()) {
        listOf(requested) + System.getenv("PATHEXT").orEmpty().split(';')
            .filter { it.isNotEmpty() }.map { requested + it.lowercase() }
    } else listOf(requested)
    for (entry in path.split(File.pathSeparatorChar)) {
        val directory = workingDirectory.resolve(entry.ifEmpty { "." })
        names.firstNotNullOfOrNull { executable(directory.resolve(it)) }?.let { return it }
    }
    if (override == null && System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        for (prefix in listOf("/opt/homebrew/bin", "/usr/local/bin")) {
            executable(File(prefix, name))?.let { return it }
        }
    }
    failure()
}

fun liveboardManagedPaths(): Set<String> {
    val kotlin = "composeApp/src/commonMain/kotlin/ui/design/generated/"
    val resources = "composeApp/src/commonMain/composeResources/"
    val tokenNames = listOf(
        "OrbitTokens", "LiveboardTokens", "LiveboardGeometry", "LiveboardMotion", "LiveboardTimelines",
        "LiveboardMotionSchema", "LiveboardScriptedGeometry", "LiveboardMaterials", "LiveboardStyles",
        "LiveboardGlyphs", "LiveboardDeviceArt", "LiveboardUtilityArt", "LiveboardIcons",
        "LiveboardStyleRulesA", "LiveboardStyleRulesB",
    )
    val orbitIcons = listOf("apple", "approved", "back", "cancel", "create", "enter", "fusion", "join", "ok", "qr", "request")
    val liveboardIcons = listOf(
        "approve", "back", "bell", "brand", "cancel", "close", "copy", "create", "current", "decline",
        "delete", "error", "hide", "lock", "paste", "pending", "pending_device", "plus", "qr", "remove",
        "reset", "search", "seed", "show",
    )
    return (
        tokenNames.map { "$kotlin$it.kt" } +
            orbitIcons.map { "${resources}drawable/orbit_$it.xml" } +
            liveboardIcons.map { "${resources}drawable/liveboard_$it.xml" } +
            listOf("geometry", "motion", "materials", "styles").map { "${resources}files/liveboard_$it.json" } +
            listOf(
                "${resources}files/liveboard_brand.svg",
                "${resources}font/orbit_manrope_regular.otf",
                "${resources}files/orbit_manrope_license.txt",
            )
        ).toSet()
}

fun orbitManagedPath(path: String): Boolean =
    path == "composeApp/src/commonMain/kotlin/ui/design/generated/OrbitTokens.kt" ||
        path in liveboardManagedPaths() ||
        Regex(
            "composeApp/src/commonMain/composeResources/" +
                "(drawable/orbit_[a-z]+\\.xml|font/orbit_manrope_regular\\.otf|files/orbit_manrope_license\\.txt)",
        ).matches(path)

fun orbitManagedFile(repo: File, path: String): File {
    if (!orbitManagedPath(path)) throw GradleException("Unexpected managed design path: $path")
    val expected = repo.canonicalFile.resolve(path)
    if (expected.canonicalFile != expected || Files.isSymbolicLink(expected.toPath())) {
        throw GradleException("Design resources must not use symbolic links: $path")
    }
    return expected
}

fun readOrbitLock(file: File): OrbitDesignLock {
    if (!file.isFile || Files.isSymbolicLink(file.toPath())) {
        throw GradleException("Missing regular design-system.lock.json; restore the pinned design exports.")
    }
    val json = JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("Invalid design-system.lock.json object.")
    if (json["package"] != "@meta-secret/design-system") {
        throw GradleException("Unexpected design-system package in client lock.")
    }
    val version = json["version"] as? String
        ?: throw GradleException("Design-system lock has no version.")
    if (!Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?").matches(version)) {
        throw GradleException("Invalid pinned design-system version.")
    }
    val sha256 = Regex("[a-f0-9]{64}")
    if (!sha256.matches(json["sourceManifestSha256"] as? String ?: "")) {
        throw GradleException("Invalid source manifest SHA-256 in client lock.")
    }
    val entries = json["files"] as? Map<*, *>
        ?: throw GradleException("Design-system lock has no files map.")
    val files = entries.entries.associate { (key, value) ->
        val path = key as? String ?: throw GradleException("Invalid managed design path.")
        val hash = value as? String ?: throw GradleException("Invalid design resource SHA-256: $path")
        if (!orbitManagedPath(path) || !sha256.matches(hash)) {
            throw GradleException("Invalid managed design resource: $path")
        }
        path to hash
    }
    val required = setOf(
        "composeApp/src/commonMain/kotlin/ui/design/generated/OrbitTokens.kt",
        "composeApp/src/commonMain/composeResources/font/orbit_manrope_regular.otf",
        "composeApp/src/commonMain/composeResources/files/orbit_manrope_license.txt",
    )
    if (!files.keys.containsAll(required)) {
        throw GradleException("Design-system lock is missing required tokens/font/license exports.")
    }
    if (version == "0.3.1") {
        val accepted = liveboardManagedPaths()
        if (!files.keys.containsAll(accepted)) {
            throw GradleException("Design-system lock is missing required 0.3.1 exports: ${accepted - files.keys}")
        }
        if (files.keys != accepted) {
            throw GradleException("Unexpected managed 0.3.1 design exports: ${files.keys - accepted}")
        }
    }
    return OrbitDesignLock(version, files)
}

fun orbitSha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

val designMode = providers.gradleProperty("designSystemMode").orElse("auto").get()
if (designMode !in setOf("auto", "workspace", "pinned")) {
    throw GradleException("designSystemMode must be auto, workspace or pinned.")
}
val designPackage = rootDir.parentFile.resolve("design-system")
val designPackageJson = designPackage.resolve("package.json")
if (designMode == "workspace" && !designPackageJson.isFile) {
    throw GradleException("Workspace design-system/package.json is missing; use pinned mode for a standalone checkout.")
}
val workspaceDesign = designMode != "pinned" && designPackageJson.isFile
val designLock = rootDir.resolve("design-system.lock.json")
val managedDesignFiles = providers.provider {
    readOrbitLock(designLock).files.keys.map { orbitManagedFile(rootDir, it) }
}

val syncDesign = if (workspaceDesign) {
    val tools = providers.provider {
        val path = providers.environmentVariable("PATH").orElse("").get()
        val node = orbitExecutable("node", providers.gradleProperty("designSystemNode").orNull, path, designPackage)
        val npm = orbitExecutable("npm", providers.gradleProperty("designSystemNpm").orNull, path, designPackage)
        val nodeName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "node.exe" else "node"
        val childNode = File(node.parentFile, nodeName)
        if (!childNode.isFile || !childNode.canExecute() || childNode.canonicalFile != node.canonicalFile) {
            throw GradleException(
                "Selected designSystemNode '$node' is not discoverable as $nodeName by npm children. " +
                    "Set -PdesignSystemNode=/absolute/path/to/$nodeName with the same executable in its parent directory.",
            )
        }
        OrbitDesignTools(node, npm, listOf(node.parent, npm.parent, path).joinToString(File.pathSeparator))
    }
    fun Exec.orbitCommand(arguments: (OrbitDesignTools) -> List<String>) {
        doFirst {
            val selected = tools.get()
            environment("PATH", selected.childPath)
            commandLine(arguments(selected))
        }
    }
    val checkToolchain = tasks.register<Exec>("checkDesignSystemToolchain") {
        group = "design system"
        description = "Checks workspace identity, Node >=22 and executable npm before design generation."
        workingDir(designPackage)
        orbitCommand { selected ->
            listOf(selected.node.path, "--eval", """
                if (+process.versions.node.split('.')[0] < 22)
                  throw Error('Design generation requires Node >=22; set -PdesignSystemNode=/absolute/path/to/node');
            """.trimIndent())
        }
        doFirst {
            val source = JsonSlurper().parse(designPackageJson) as? Map<*, *>
                ?: throw GradleException("Invalid shared design package.json.")
            val pinned = readOrbitLock(designLock)
            if (source["name"] != "@meta-secret/design-system" || source["version"] != pinned.version) {
                throw GradleException(
                    "Shared design package identity/version differs from the client pin. " +
                        "Adopt the new package with an explicit shared export/sync first, or use -PdesignSystemMode=pinned.",
                )
            }
        }
    }
    val installDependencies = tasks.register<Exec>("installDesignSystemDependencies") {
        group = "design system"
        description = "Installs pinned design dependencies only when package inputs or node_modules change."
        dependsOn(checkToolchain)
        workingDir(designPackage)
        orbitCommand { listOf(it.npm.path, "ci", "--ignore-scripts", "--no-audit", "--no-fund") }
        inputs.files(designPackageJson, designPackage.resolve("package-lock.json"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir(designPackage.resolve("node_modules"))
    }
    val buildDesign = tasks.register<Exec>("buildDesignSystem") {
        group = "design system"
        description = "Generates shared deterministic design exports in the workspace package."
        dependsOn(installDependencies)
        workingDir(designPackage)
        orbitCommand { listOf(it.node.path, "scripts/build.mjs") }
        inputs.files(designPackageJson, designPackage.resolve("package-lock.json"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(designPackage.resolve("tokens")), fileTree(designPackage.resolve("assets")))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(designPackage.resolve("scripts")))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(designPackage.resolve("liveboard")), designPackage.resolve("brand/metasecret-gray.svg"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("nodeExecutable", tools.map { it.node.path })
        outputs.dir(designPackage.resolve("dist"))
    }
    tasks.register<Exec>("syncDesignSystem") {
        group = "design system"
        description = "Synchronizes only this Compose client's generated files and version lock."
        dependsOn(buildDesign)
        workingDir(designPackage)
        orbitCommand { listOf(it.node.path, "scripts/sync.mjs", "--client", "compose") }
        inputs.files(fileTree(designPackage.resolve("dist/compose")), designPackage.resolve("dist/manifest.json"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(designPackage.resolve("scripts")))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("nodeExecutable", tools.map { it.node.path })
        outputs.files(managedDesignFiles, designLock)
    }
} else {
    null
}

val verifyDesign = tasks.register("verifyDesignSystem") {
    group = "verification"
    description = "Verifies pinned Compose design exports and safe paths using JVM SHA-256, without Node."
    if (syncDesign != null) dependsOn(syncDesign)
    inputs.file(designLock).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(managedDesignFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("design-system/verified.sha256")
    outputs.file(marker)
    doLast {
        val pinned = readOrbitLock(designLock)
        for ((path, expectedHash) in pinned.files) {
            val resource = orbitManagedFile(rootDir, path)
            if (!resource.isFile || orbitSha256(resource) != expectedHash) {
                throw GradleException("Pinned design resource missing or changed: $path")
            }
        }
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${orbitSha256(designLock)}\n")
        }
        logger.lifecycle("Verified design system {} ({} managed Compose exports).", pinned.version, pinned.files.size)
    }
}

tasks.register("prepareDesignSystem") {
    group = "design system"
    description = "Prepares workspace design exports, or verifies the standalone pinned exports."
    dependsOn(verifyDesign)
    doLast {
        logger.lifecycle("Design system mode: {}", if (workspaceDesign) "workspace" else "pinned")
    }
}
