import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    eclipse
    application
    kotlin("jvm") version "1.9.20"
    id("io.github.goooler.shadow") version "8.1.7"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
	implementation("net.minestom:minestom-snapshots:7b659f0fc3")
    implementation("commons-codec:commons-codec:1.16.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

val mainClassPath = "fr.sunderia.bomberman.BombermanKt"

application {
    mainClass.set(mainClassPath)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar> {
    //minimize()
}

tasks.withType<Jar> {
    manifest {
        // Change this to your main class
        attributes["Main-Class"] = mainClassPath
    }
}
