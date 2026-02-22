import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.*

val publishingToken = System.getenv("JB_PUB_TOKEN") ?: null
val publishingChannel = System.getenv("JB_PUB_CHANNEL") ?: "default"
// set by default in Github Actions
val isCI = System.getenv("CI") != null

fun prop(name: String): String =
    extra.properties[name] as? String
        ?: error("Property `$name` is not defined in gradle.properties for environment `$shortPlatformVersion`")

fun propOrNull(name: String): String? = extra.properties[name] as? String

fun execAndGetStdout(vararg args: String): String =
    providers.exec {
        commandLine(args.toList())
    }.standardOutput.asText.get().trim()

fun gitCommitHash(): String =
    execAndGetStdout("git", "rev-parse", "--short", "HEAD").also {
        if (it == "HEAD")
            logger.warn("Unable to determine current branch: Project is checked out with detached head!")
    }

fun gitTimestamp(): String =
    execAndGetStdout("git", "show", "--no-patch", "--format=%at", "HEAD").also {
        if (it == "HEAD")
            logger.warn("Unable to determine current branch: Project is checked out with detached head!")
    }

val psiViewerPlugin: String by project
val shortPlatformVersion = prop("shortPlatformVersion")
val shortPlatformVersionInt = shortPlatformVersion.toIntOrNull() ?: 0
val useInstallerFlag = prop("useInstaller").toBooleanStrict()
val verifierUseInstallerFlag = propOrNull("verifierUseInstaller")?.toBooleanStrictOrNull() ?: true
val verifierAllowInternalApiUsages = propOrNull("verifierAllowInternalApiUsages")?.toBooleanStrictOrNull() ?: false
val codeVersion = "1.6.2"
var pluginVersion = "$codeVersion.$shortPlatformVersion"
if (publishingChannel != "default") {
    // timestamp of the commit with this eaps addition
    val start = 1714498465
    val commitTimestamp = gitTimestamp().toInt() - start
    pluginVersion = "$pluginVersion-$publishingChannel.$commitTimestamp"
}

val pluginGroup = "org.sui"
val pluginName = "intellij-sui-move"
val pluginJarName = "intellij-sui-move-$pluginVersion"
val targetBytecodeVersion = if (shortPlatformVersionInt >= 253) JavaVersion.VERSION_21 else JavaVersion.VERSION_17

val kotlinReflectVersion = "2.2.20"

group = pluginGroup
version = pluginVersion

plugins {
    id("java")
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.grammarkit") version "2023.3.0.2"
    id("net.saliman.properties") version "1.5.2"
    id("org.gradle.idea")
    id("de.undercouch.download") version "5.5.0"
}

allprojects {
    apply {
        plugin("kotlin")
        plugin("org.jetbrains.grammarkit")
        plugin("org.jetbrains.intellij.platform")
        plugin("de.undercouch.download")
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
        intellijPlatform {
            defaultRepositories()
            jetbrainsRuntime()
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinReflectVersion")

        implementation("io.sentry:sentry:7.2.0") {
            exclude("org.slf4j")
        }
        implementation("com.github.ajalt.clikt:clikt:3.5.2")
        implementation("org.apache.commons:commons-text:1.10.0")
        testImplementation("junit:junit:4.13.2")

        intellijPlatform {
//            plugins(listOf(psiViewerPlugin))
            val platformType = prop("platformType")
            val platformVersion = prop("platformVersion")
            if (platformType == "IC" && shortPlatformVersionInt >= 253) {
                intellijIdea(platformVersion) {
                    useInstaller.set(useInstallerFlag)
                }
            } else {
                create(platformType, platformVersion) {
                    useInstaller.set(useInstallerFlag)
                }
            }
            testFramework(TestFrameworkType.Platform)
            pluginVerifier(Constraints.LATEST_VERSION)
            bundledPlugin("org.toml.lang")
            jetbrainsRuntime()
        }
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = targetBytecodeVersion
        targetCompatibility = targetBytecodeVersion
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    sourceSets {
        main {
            java.srcDirs("src/main/gen")
        }
    }

    kotlin {
        jvmToolchain(21)
        if (file("src/$shortPlatformVersion/main/kotlin").exists()) {
            sourceSets {
                main {
                    kotlin.srcDirs("src/$shortPlatformVersion/main/kotlin")
                }
            }
        }
    }

    intellijPlatform {
        pluginConfiguration {
            version.set(pluginVersion)
            ideaVersion {
                sinceBuild.set(prop("pluginSinceBuild"))
                val untilBuildValue = propOrNull("pluginUntilBuild")?.trim().orEmpty()
                if (untilBuildValue.isNotEmpty()) {
                    untilBuild.set(untilBuildValue)
                }
            }
            val codeVersionForUrl = codeVersion.replace('.', '-')
            changeNotes.set(
                """
                    <body>
                        <p><a href="https://intellij-move.github.io/$codeVersionForUrl.html">
                            Changelog for the Intellij-Move $codeVersion
                            </a></p>
                    </body>
                """
            )

        }

        instrumentCode.set(false)

        publishing {
            token.set(publishingToken)
            channels.set(listOf(publishingChannel))
        }

        pluginVerification {
            ides {
                val verifierIdeVersion = propOrNull("verifierIdeVersion")?.trim()
                if (!verifierIdeVersion.isNullOrEmpty()) {
                    val (type, version) = verifierIdeVersion.split('-', limit = 2)
                    create(type, version) {
                        useInstaller.set(verifierUseInstallerFlag)
                    }
                } else {
                    recommended()
                }
            }
            //if("SNAPSHOT"!inshortPlatformVersion){
            //ides{
            //ide(prop("verifierIdeVersion").trim())
            //}
            //}
            val toleratedFailureLevels = EnumSet.of(
                VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
                VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            ).apply {
                if (verifierAllowInternalApiUsages) {
                    add(VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES)
                }
            }
            failureLevel.set(
                EnumSet.complementOf(
                    toleratedFailureLevels
                )
            )
        }
    }
    tasks {
        jar {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        generateLexer {
            sourceFile.set(file("src/main/grammars/MoveLexer.flex"))
            targetOutputDir.set(file("src/main/gen/org/sui/lang"))
//            purgeOldFiles.set(true)
        }
        generateParser {
            sourceFile.set(file("src/main/grammars/MoveParser.bnf"))
            targetRootOutputDir.set(file("src/main/gen"))
            // not used if purgeOldFiles set to false
            pathToParser.set("/org/sui/lang/MoveParser.java")
            pathToPsiRoot.set("/org/sui/lang/core/psi")
//            purgeOldFiles.set(true)
        }

        withType<KotlinCompile> {
            dependsOn(generateLexer, generateParser)
            compilerOptions {
                jvmTarget.set(if (shortPlatformVersionInt >= 253) JvmTarget.JVM_21 else JvmTarget.JVM_17)
                languageVersion.set(KotlinVersion.KOTLIN_2_2)
                apiVersion.set(KotlinVersion.KOTLIN_2_2)
                freeCompilerArgs.add("-Xjvm-default=all")
            }
        }

    }

    val runIdeWithPlugins by intellijPlatformTesting.runIde.registering {
        plugins {
            plugin("com.google.ide-perf:1.3.1")
//            plugin("PsiViewer:PsiViewer 241.14494.158-EAP-SNAPSHOT")
        }
        task {
            systemProperty("org.sui.debug.enabled", true)
            systemProperty("org.sui.types.highlight.unknown.as.error", true)
//            systemProperty("org.move.external.linter.max.duration", 30)  // 30 ms
//            systemProperty("org.move.aptos.bundled.force.unsupported", true)
//            systemProperty("idea.log.debug.categories", "org.move.cli")
        }

        prepareSandboxTask {
            // dependsOn("downloadAptosBinaries")
            // copyDownloadedAptosBinaries(this)
        }
    }

    tasks.register("resolveDependencies") {
        doLast {
            rootProject.allprojects
                .map { it.configurations }
                .flatMap { it.filter { c -> c.isCanBeResolved } }
                .forEach { it.resolve() }
        }
    }

    idea {
        pathVariables(mapOf("USER_HOME" to file("/Users/yu")))
        module {
            name = "intellij-sui-move.main"
        }
    }
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        // If cache path doesn't exist, we need to create it manually
        // because otherwise gradle-intellij-plugin will ignore it
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }
