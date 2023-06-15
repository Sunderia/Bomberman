plugins {
    java
    eclipse
    kotlin("jvm") version "1.8.22"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
	compileOnly("com.github.Minestom.Minestom:Minestom:954e8b3915")
    compileOnly("commons-codec:commons-codec:1.15")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}