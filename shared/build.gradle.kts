plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.oshai:kotlin-logging:8.0.03")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("ai.koog:koog-agents:1.1.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            implementation("ai.koog:agents-mcp:1.1.1-beta")
            implementation("org.slf4j:slf4j-api:2.0.17")
            implementation("ch.qos.logback:logback-classic:1.5.18")
            implementation("io.ktor:ktor-client-java:3.3.3")
            implementation("org.xerial:sqlite-jdbc:3.53.1.0")
        }
        jvmTest.dependencies {}
    }
}
