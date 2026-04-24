import org.gradle.api.tasks.Sync

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.agent"
version = "0.0.1"

val ktorVersion = "3.2.3"

dependencies {
    implementation("ai.koog:koog-agents:0.8.0")
    implementation("ai.koog:agents-features-acp:0.8.0")
    implementation("ai.koog:agents-mcp:0.8.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "com.agent.runtime.server.RuntimeHttpServerKt"
}

val cliHostMainClass = "com.agent.runtime.cli.RuntimeCliHostKt"

tasks.register<Sync>("installCliHostDist") {
    group = "application"
    description = "Installs start scripts for the runtime stdio CLI host."
    dependsOn(tasks.named("jar"))
    into(layout.buildDirectory.dir("cli-host"))
    from(tasks.named("jar")) {
        into("lib")
    }
    from(configurations.runtimeClasspath) {
        into("lib")
    }
    doLast {
        val binDir = layout.buildDirectory.dir("cli-host/bin").get().asFile
        if (!binDir.exists()) {
            binDir.mkdirs()
        }

        val windowsScript = binDir.resolve("runtime-cli-host.bat")
        windowsScript.writeText(
            """
            @echo off
            set DIRNAME=%~dp0
            set APP_HOME=%DIRNAME%..
            for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi
            if defined JAVA_HOME (
              set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
            ) else (
              set "JAVA_EXE=java.exe"
            )
            "%JAVA_EXE%" %JAVA_OPTS% %RUNTIME_CLI_HOST_OPTS% -cp "%APP_HOME%\lib\*" $cliHostMainClass %*
            """.trimIndent(),
        )
    }
}

tasks.register<JavaExec>("runCliHost") {
    group = "application"
    description = "Runs the runtime stdio CLI host."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(cliHostMainClass)
    standardInput = System.`in`
}
