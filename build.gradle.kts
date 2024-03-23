plugins {
    java
    eclipse
    application
    kotlin("jvm") version "1.8.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
	implementation("net.minestom:minestom-snapshots:7320437640")
    implementation("commons-codec:commons-codec:1.16.1")
}

val mainClassPath = "fr.sunderia.bomberman.BombermanKt"

application {
    mainClass.set(mainClassPath)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Jar> {
    manifest {
        // Change this to your main class
        attributes["Main-Class"] = mainClassPath
    }
}