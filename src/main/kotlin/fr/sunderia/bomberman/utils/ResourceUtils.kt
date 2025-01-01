package fr.sunderia.bomberman.utils

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Arrays
import java.util.Objects

object ResourceUtils {
    fun getPaths(uri: URI): List<Path> {
        return Arrays.stream(Objects.requireNonNull(Paths.get(uri).toFile().listFiles { _, name: String -> name.endsWith(".json") }))
            .map(File::toPath).toList()
    }
}

