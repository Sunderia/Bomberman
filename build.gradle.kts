plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
	implementation("com.github.Minestom.Minestom:Minestom:a9e319f961")
    compileOnly("commons-codec:commons-codec:1.15")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}