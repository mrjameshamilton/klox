package eu.jameshamilton.klox.util

import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream

fun readFiles(path: String): Sequence<Path> {
    return sequence {
        val uri: URI = object {}::class.java.getResource(path).toURI()
        val myPath: Path = if (uri.scheme.equals("jar")) {
            val fileSystem: FileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fileSystem.getPath(path)
        } else {
            Paths.get(uri)
        }
        val walk: Stream<Path> = Files.walk(myPath)
        val it: Iterator<Path> = walk.iterator()
        while (it.hasNext()) {
            yield(it.next())
        }
    }
}
