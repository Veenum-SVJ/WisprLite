package com.edib.openwhispr

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.util.UUID
import java.nio.file.Files

object SafeModelArchive {
    fun extract(archive: File, outDir: File) {
        require(outDir.mkdirs() || outDir.isDirectory)
        val root = outDir.canonicalFile
        var total = 0L
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            generateSequence { tar.nextTarEntry }.forEach { entry ->
                val destination = File(root, entry.name).canonicalFile
                require(destination.path.startsWith(root.path + File.separator)) { "Invalid archive path" }
                require(!entry.isSymbolicLink && !entry.isLink && (entry.isDirectory || entry.isFile)) { "Unsupported archive entry" }
                require(entry.size in 0..2_147_483_648L) { "Model file exceeds size limit" }
                total += entry.size
                require(total <= 4_294_967_296L) { "Model archive exceeds size limit" }
                if (entry.isDirectory) require(destination.mkdirs() || destination.isDirectory)
                else {
                    require(destination.parentFile.mkdirs() || destination.parentFile.isDirectory)
                    destination.outputStream().use { output -> tar.copyTo(output) }
                    require(destination.length() == entry.size) { "Incomplete model file" }
                }
            }
        }
    }

    fun install(archive: File, modelsRoot: File, modelName: String, validate: (File) -> Boolean) {
        require(modelName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")))
        require(modelsRoot.mkdirs() || modelsRoot.isDirectory)
        val root = modelsRoot.canonicalFile
        val staging = File(root, ".staging-${UUID.randomUUID()}")
        val backup = File(root, ".replaced-${UUID.randomUUID()}")
        val destination = File(root, modelName)
        try {
            extract(archive, staging)
            val candidate = File(staging, modelName)
            require(candidate.isDirectory && validate(candidate)) { "Downloaded model is incomplete or incompatible" }
            if (destination.exists()) Files.move(destination.toPath(), backup.toPath())
            try { Files.move(candidate.toPath(), destination.toPath()) }
            catch (failure: Exception) {
                if (backup.exists()) Files.move(backup.toPath(), destination.toPath())
                throw failure
            }
            if (backup.exists()) backup.deleteRecursively()
        } finally { staging.deleteRecursively() }
    }
}

