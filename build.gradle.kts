plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

group = "com.agent"
version = "0.1.0"

allprojects {
    repositories {
        google()
        // Temporary direct Central URL: the user-level Gradle init script rewrites
        // the standard mavenCentral() URL to an Aliyun mirror that has not synced Koog 1.1.1 yet.
        // todo 过段时间删掉
        maven {
            url = uri("https://repo.maven.apache.org:443/maven2")
        }
    }
}
