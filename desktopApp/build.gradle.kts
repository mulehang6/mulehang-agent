import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.file.RelativePath
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar

val jewelVersion = "0.39.1-262.9437.29"
val intellijPlatformVersion = "262.9437.29"
val mermaidVersion = "11.15.0"

val jcefHome = file(
    providers.gradleProperty("jcefHome").orNull
        ?: System.getenv("JCEF_HOME")
        ?: "D:/jdk/jbrsdk-JCEF",
)
val jcefJmod = jcefHome.resolve("jmods/jcef.jmod")
val jcefHelper = jcefHome.resolve("bin/jcef_helper.exe")

val jcefRuntimeHint =
    "Point -PjcefHome=... or the JCEF_HOME env var at a JetBrains Runtime with JCEF."
check(jcefJmod.isFile) {
    "The configured JCEF runtime does not contain ${jcefJmod.path}. $jcefRuntimeHint"
}
check(jcefHelper.isFile) {
    "The configured JCEF runtime does not contain ${jcefHelper.path}. $jcefRuntimeHint"
}

val mermaidWebJar = configurations.create("mermaidWebJar") {
    isTransitive = false
}
dependencies.add(mermaidWebJar.name, "org.webjars.npm:mermaid:$mermaidVersion")
val mermaidWebJarContents = zipTree(mermaidWebJar.singleFile)
val jcefApiJar = tasks.register<Jar>("jcefApiJar") {
    description = "Extracts the JCEF API classes from the configured JetBrains Runtime."
    archiveFileName.set("mulehang-jcef-api.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/jcef-api"))
    from(zipTree(jcefJmod)) {
        include("classes/**")
        exclude("classes/module-info.class")
        exclude("classes/META-INF/MANIFEST.MF")
        eachFile {
            path = path.removePrefix("classes/")
        }
        includeEmptyDirs = false
    }
}
val diagramAppResourcesRoot = layout.buildDirectory.dir("generated/diagram-app-resources")
val diagramDevelopmentResourcesRoot = layout.buildDirectory.dir("generated/diagram-development-resources")
val prepareDiagramAppResources = tasks.register<Sync>("prepareDiagramAppResources") {
    description = "Packages the local diagram viewer and Mermaid runtime resources."
    from(layout.projectDirectory.dir("src/main/resources/diagram")) {
        into("windows/diagram")
    }
    from(mermaidWebJarContents) {
        include("META-INF/resources/webjars/mermaid/11.15.0/dist/**")
        eachFile {
            relativePath = RelativePath(
                true,
                "windows",
                "diagram",
                "mermaid",
                *relativePath.segments.drop(9).toTypedArray(),
            )
        }
        includeEmptyDirs = false
        into("windows/diagram/mermaid")
    }
    from(mermaidWebJarContents) {
        include("META-INF/resources/webjars/mermaid/11.15.0/LICENSE")
        eachFile {
            relativePath = RelativePath(true, "windows", "diagram", "mermaid", "LICENSE-mermaid.txt")
        }
        into("windows/diagram/mermaid")
    }
    into(diagramAppResourcesRoot)
}
val prepareDiagramDevelopmentResources = tasks.register<Sync>("prepareDiagramDevelopmentResources") {
    description = "Places the packaged offline diagram resources on the development classpath."
    dependsOn(prepareDiagramAppResources)
    from(diagramAppResourcesRoot.map { resourcesRoot -> resourcesRoot.dir("windows/diagram") }) {
        exclude("diagram.html")
        into("diagram")
    }
    into(diagramDevelopmentResourcesRoot)
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://www.jetbrains.com/intellij-repository/releases")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material", module = "material")
        exclude(group = "org.jetbrains.compose.material3", module = "material3")
    }
    implementation("org.jetbrains.compose.components:components-resources:1.11.0")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-core:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-extensions-autolink:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-strikethrough:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-tables:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-extensions-images:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-markdown-int-ui-standalone-styling:$jewelVersion")
    implementation("com.jetbrains.intellij.platform:icons-api:$intellijPlatformVersion")
    implementation("com.jetbrains.intellij.platform:icons-impl:$intellijPlatformVersion")
    implementation("com.jetbrains.intellij.platform:icons:262.9437.214")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.66")
    implementation("org.jetbrains.jediterm:jediterm-ui:3.66")
    implementation("org.jetbrains.pty4j:pty4j:0.13.12")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("net.sourceforge.plantuml:plantuml-mit:1.2026.6")
    implementation(files(jcefApiJar))
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.4.0")
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("org.apache.xmlgraphics:batik-codec:1.19")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    /*
     * Hot Reload publishes its own runtime only under a custom Usage. Normalizing that one
     * metadata variant lets the generated classpath consume it together with ordinary JVM jars.
     */
    components {
        withModule("org.jetbrains.compose.hot-reload:hot-reload-runtime-jvm") {
            withVariant("shadowRuntimeElements") {
                attributes {
                    attribute(
                        Usage.USAGE_ATTRIBUTE,
                        objects.named(Usage::class.java, "java-runtime"),
                    )
                }
            }
        }
    }
}

/*
 * Compose Hot Reload 1.1.1's Dev classpath is only meaningful for modules that publish a
 * Compose-specific variant. This application and :shared publish standard JVM runtime variants,
 * so resolve that generated classpath as a normal runtime classpath instead.
 */
afterEvaluate {
    configurations.named("composeHotReloadDevRuntimeClasspath") {
        attributes.attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage::class.java, "java-runtime"),
        )
    }
}

kotlin {
    jvmToolchain(25)
}

sourceSets.named("main") {
    resources.srcDir(diagramDevelopmentResourcesRoot)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.agent.app.bootstrap.MainKt"
        javaHome = jcefHome.absolutePath
        jvmArgs += "--add-modules=jcef"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "mulehang-agent"
            packageVersion = "0.1.0"
            modules("jcef")
            appResourcesRootDir.set(diagramAppResourcesRoot)
        }
    }
}

tasks.matching { task -> task.name.startsWith("prepareAppResources") }.configureEach {
    dependsOn(prepareDiagramAppResources)
}
tasks.named("processResources") {
    dependsOn(prepareDiagramDevelopmentResources)
}

/*
 * JLink keeps JCEF's native DLLs from the `jcef` module, but its Windows helper executable
 * lives outside the module image. Chromium subprocesses require that executable rather than
 * the main Java launcher, so copy it only after Compose has created the runtime image and
 * before it is assembled into a distributable.
 */
val copyJcefHelperToRuntime = tasks.register<Copy>("copyJcefHelperToRuntime") {
    description = "Copies the JBR JCEF helper into the Compose runtime image."
    dependsOn("createRuntimeImage")
    from(jcefHelper)
    into(layout.buildDirectory.dir("compose/tmp/main/runtime/bin"))
}

tasks.matching {
    it.name == "createDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name.startsWith("package")
}.configureEach {
    dependsOn(copyJcefHelperToRuntime)
}
