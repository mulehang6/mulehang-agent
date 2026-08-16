plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    kotlin("jvm") version "2.3.20" apply false
    kotlin("plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}

group = "com.agent"
version = "0.1.0"

allprojects {
    repositories {
        google()
        maven("https://repo.maven.apache.org:443/maven2")
        mavenCentral()
    }
}
