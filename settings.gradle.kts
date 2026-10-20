pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        maven("https://jitpack.io") { name = "Jitpack" }
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.replaymod.preprocess" -> {
                    useModule("com.github.Fallen-Breath:preprocessor:${requested.version}")
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// SGU-Printer: 仅保留 26.1 / 26.2 两个目标版本（其余版本目录已裁剪）
val versions = listOf(
    "26.1", "26.2"
)
val mainProjectVersion = file("versions/mainProject").readText().trim()
val preprocessParentChain = mapOf(
    "26.2" to "26.1"
)

fun expandWithPreprocessParents(requested: List<String>): List<String> {
    val expanded = linkedSetOf<String>()
    requested.forEach { version ->
        var current: String? = version
        while (current != null && expanded.add(current)) {
            current = preprocessParentChain[current]
        }
    }
    return expanded.toList()
}

val requestedVersions = System.getenv("TARGET_MC_VERSIONS")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()

val selectedVersions = when {
    requestedVersions.isNullOrEmpty() -> versions
    else -> {
        val unknownVersions = requestedVersions - versions.toSet()
        require(unknownVersions.isEmpty()) {
            "Unknown TARGET_MC_VERSIONS entries: ${unknownVersions.joinToString(", ")}"
        }
        expandWithPreprocessParents(requestedVersions).let { expanded ->
            if (mainProjectVersion in expanded) expanded else expanded + mainProjectVersion
        }
    }
}

for (version in selectedVersions) {
    include(":$version")
    project(":$version").apply {
        projectDir = file("versions/$version")
        buildFileName = if (parseMcVersionToNumber(version) > 260000) {
            "../../build.fabric.gradle.kts"
        } else {
            "../../build.fabric.remap.gradle.kts"
        }
    }
}

include(":fabricWrapper")

// 暂时不了解怎么写到插件里, 先丢在这里吧
fun parseMcVersionToNumber(mcVersionStr: String): Int {
    val cleanVersion = mcVersionStr.split("-")[0] // 去掉 -fabric/-pre/-rc 等后缀
        .replace(Regex("[^0-9.]"), "") // 移除所有非数字、非点的字符
    val versionParts = cleanVersion.split(".")
        .filter { it.isNotEmpty() } // 过滤空字符串（避免异常分割）
    val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = versionParts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}
