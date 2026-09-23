plugins {
    id("com.android.application") version "8.9.1" apply false
    kotlin("android") version "2.1.10" apply false
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.compose") version "2.1.10" apply false
    kotlin("kapt") version "2.1.10" apply false
    id("com.google.dagger.hilt.android") version "2.55" apply false
    id("com.diffplug.spotless") version "7.0.2"
}
subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint("1.5.0")
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.5.0")
        }
    }
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}
