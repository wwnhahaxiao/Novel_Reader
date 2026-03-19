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
        description = "Bump the patch version in version.properties"
        val versionFile = layout.projectDirectory.file("version.properties").asFile
        doLast {
            val current = versionFile.readLines().first { it.startsWith("version=") }
                .substringAfter("version=")
            val parts = current.split(".").map { it.toInt() }.toMutableList()
            parts[parts.lastIndex] = parts.last() + 1
            val next = parts.joinToString(".")
            versionFile.writeText("version=$next\n")
            println("Version bumped: $current -> $next")
        }
    }

    register("updateChangelog") {
        group = "versioning"
        description = "Prepend current version's git commits to CHANGELOG.html"
        dependsOn("bumpVersion")

        val chgFile = layout.projectDirectory.file("CHANGELOG.html").asFile
        val verFile = layout.projectDirectory.file("version.properties").asFile
        val markerFile = layout.projectDirectory.file(".last_build_commit").asFile

        doLast {
            val version = verFile.readLines()
                .first { it.startsWith("version=") }
                .substringAfter("version=")

            val cmd = if (markerFile.exists()) {
                val since = markerFile.readText().trim()
                arrayOf("git", "log", "--pretty=format:%s", "$since..HEAD")
            } else {
                arrayOf("git", "log", "--pretty=format:%s")
            }

            val process = ProcessBuilder(*cmd).start()
            val log = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (log.isNotBlank()) {
                val items = log.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "    <li>$it</li>" }
                val newSection = "<h3>v$version</h3>\n<ul>\n$items\n</ul>\n\n"

                val existing = if (chgFile.exists()) chgFile.readText() else ""
                chgFile.writeText(newSection + existing)
            }

            val headProcess = ProcessBuilder("git", "rev-parse", "HEAD").start()
            val head = headProcess.inputStream.bufferedReader().readText().trim()
            headProcess.waitFor()
            markerFile.writeText(head)

            println("Changelog updated for v$version")
        }
    }

    named("patchPluginXml") {
        dependsOn("updateChangelog")
    }

    named("buildPlugin") {
        dependsOn("updateChangelog")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
