import java.net.URL

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "roshan"
version = file("version.properties")
    .readLines().first { it.startsWith("version=") }
    .substringAfter("version=")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        // 使用 Community 版本，去除 Ultimate 限制
        intellijIdeaCommunity("2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

val changelogFile = layout.projectDirectory.file("CHANGELOG.html").asFile

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }

        changeNotes = provider {
            if (changelogFile.exists()) changelogFile.readText() else "<ul><li>Initial version</li></ul>"
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    register("bumpVersion") {
        group = "versioning"
        description = "Fetch latest version from JetBrains Marketplace and bump patch"
        val versionFile = layout.projectDirectory.file("version.properties").asFile
        doLast {
            @Suppress("DEPRECATION")
            val apiUrl = URL("https://plugins.jetbrains.com/api/plugins/30744/updates?size=1")
            val json = apiUrl.readText()
            val current = """"version"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1)
                ?: error("Failed to fetch version from JetBrains Marketplace")

            val parts = current.split(".").map { it.toInt() }.toMutableList()
            parts[parts.lastIndex] = parts.last() + 1
            val next = parts.joinToString(".")
            versionFile.writeText("version=$next\n")
            println("Marketplace: $current -> $next")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
