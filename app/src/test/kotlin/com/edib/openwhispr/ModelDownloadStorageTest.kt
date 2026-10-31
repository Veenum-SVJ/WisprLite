package com.edib.openwhispr

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadStorageTest {
    @Test(timeout = 5_000) fun interruptedHttpBodyIsNotAcceptedAsACompleteModel() {
        val directory = java.nio.file.Files.createTempDirectory("wispr-transfer-test").toFile()
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val peer = kotlin.concurrent.thread {
            try {
                server.accept().use { socket ->
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\nConnection: close\r\n\r\nshort".toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                }
            } finally { server.close() }
        }
        try {
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                ModelDownloader.downloadFile("http://127.0.0.1:$port/model", java.io.File(directory, "partial.tar.bz2"), "Empty model") { }
            }
            peer.join(2_000)
            org.junit.Assert.assertFalse(peer.isAlive)
        } finally { server.close(); directory.deleteRecursively() }
    }
    @Test fun smallDownloadBudgetCoversTheVerifiedArchiveAndExtractedFilesTogether() {
        val small = MODEL_CATALOG.single { it.archive == "sherpa-onnx-whisper-small" }
        // Measured from the official release asset and its full tar listing.
        val compressedBytes = 639_387_718L
        val extractedBytes = 1_344_966_809L
        assertTrue(ModelDownloader.minimumFreeBytes(small) >= compressedBytes + extractedBytes)
    }
}
