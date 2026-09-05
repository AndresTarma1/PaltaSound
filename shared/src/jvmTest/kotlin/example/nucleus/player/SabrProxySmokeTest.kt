package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica que el proxy SABR local sirve rangos (incluido seek) a mpv. */
class SabrProxySmokeTest {
    private fun heap(tag: String) {
        System.gc()
        Thread.sleep(500)
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
        val nonHeap = java.lang.management.ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used / 1_048_576L
        println("MEM $tag heapUsed=${used}MB nonHeap=${nonHeap}MB")
    }

    @Test
    fun sabrMemoryProbe() = runBlocking {
        heap("antes-resolve")
        val url = SabrResolver.resolveLocalUrl("YckmB9-uKxw", AudioQuality.NORMAL)
            ?: error("sabr resolve null")
        heap("tras-resolve")
        println("PROXY url=$url")
        fun get(range: String?): Int {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            if (range != null) c.setRequestProperty("Range", range)
            c.connectTimeout = 30000
            c.readTimeout = 60000
            val code = c.responseCode
            val n = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.size ?: 0
            c.disconnect()
            println("PROXY range=$range => $code n=$n")
            return n
        }
        get("bytes=0-1048575")
        heap("tras-1MB")
        // Drenar el resto para forzar el fetch completo y medir retención final.
        get(null)
        heap("tras-completo")
    }

    @Test
    fun sabrProxyServesRanges() = runBlocking {
        val url = SabrResolver.resolveLocalUrl("YckmB9-uKxw", AudioQuality.NORMAL)
            ?: error("sabr resolve null")
        println("PROXY url=$url")
        assertTrue(url.startsWith("http://127.0.0.1:"), "proxy URL local esperada")

        fun get(range: String?): Triple<Int, String, Int> {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            if (range != null) c.setRequestProperty("Range", range)
            c.connectTimeout = 20000
            c.readTimeout = 20000
            val code = c.responseCode
            val contentRange = c.getHeaderField("Content-Range") ?: ""
            val bytes = (if (code < 400) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
            c.disconnect()
            return Triple(code, contentRange, bytes.size)
        }

        val (c1, h1, n1) = get("bytes=0-2047")
        println("PROXY first => $c1 [$h1] n=$n1")
        assertEquals(206, c1)
        assertEquals(2048, n1)

        // Seek al MB 1: abre un flujo SABR nuevo en el tiempo estimado y salta por startRange.
        val (c2, h2, n2) = get("bytes=1048576-1052671")
        println("PROXY seek => $c2 [$h2] n=$n2")
        assertEquals(206, c2)
        assertEquals(4096, n2)
        assertTrue(h2.startsWith("bytes 1048576-"), "Content-Range esperado, fue: $h2")
    }
}
