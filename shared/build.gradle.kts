plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
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
            implementation("app.cash.sqldelight:runtime:2.4.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.4.0")
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
            implementation("app.cash.sqldelight:sqlite-driver:2.4.0")
        }
        jvmTest.dependencies {}
    }
}

sqldelight {
    databases {
        create("MulehangDatabase") {
            packageName.set("com.agent.shared.persistence.db")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.0")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
