plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

group = "com.agent"
version = "0.1.0"

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
