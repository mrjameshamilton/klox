package eu.jameshamilton.klox.io

import proguard.classfile.ClassPool
import proguard.io.DataEntry
import proguard.io.DataEntryClassWriter
import proguard.io.DataEntryWriter
import proguard.io.FixedFileWriter
import proguard.io.JarWriter
import proguard.io.ZipWriter
import java.io.File
import java.io.OutputStream

fun writeJar(programClassPool: ClassPool, file: File, mainClass: String) {
    class MyJarWriter(zipEntryWriter: DataEntryWriter) : JarWriter(zipEntryWriter) {
        override fun createManifestOutputStream(manifestEntry: DataEntry): OutputStream {
            val outputStream = super.createManifestOutputStream(manifestEntry)
            val writer = outputStream.writer()
            writer.appendLine("Main-Class: $mainClass")
            writer.flush()
            return outputStream
        }
    }

    val jarWriter = MyJarWriter(
        ZipWriter(
            FixedFileWriter(
                file
            )
        )
    )

    programClassPool.classesAccept(
        DataEntryClassWriter(jarWriter)
    )

    jarWriter.close()
}
