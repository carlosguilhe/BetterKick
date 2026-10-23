package com.newkick.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SpeedResult(val singleMbps: Double, val parallelMbps: Double) {
    val best get() = maxOf(singleMbps, parallelMbps)

    /** Maior qualidade que cabe com folga (os bitrates da Kick: 9, 3.4, 1.4, 0.6 Mbps). */
    val recommendation: String
        get() = when {
            best >= 12 -> "1080p60"
            best >= 5 -> "720p60"
            best >= 2.2 -> "480p"
            best >= 1 -> "360p"
            else -> "160p"
        }
}

/** Mede a banda real do aparelho: 1 conexão (como o player usa) e 4 conexões em paralelo. */
object SpeedTest {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun download(bytes: Long): Long {
        val request = Request.Builder()
            .url("https://speed.cloudflare.com/__down?bytes=$bytes")
            .header("User-Agent", UA)
            .build()
        var total = 0L
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return 0L
            val stream = response.body?.source()?.inputStream() ?: return 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
            }
        }
        return total
    }

    private fun mbps(bytes: Long, startNs: Long) = bytes * 8.0 / 1e6 / ((System.nanoTime() - startNs) / 1e9)

    suspend fun run(): SpeedResult = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        val singleMbps = mbps(download(8_000_000), t0)

        val t1 = System.nanoTime()
        val total = coroutineScope {
            (1..4).map { async(Dispatchers.IO) { runCatching { download(5_000_000) }.getOrDefault(0L) } }.awaitAll().sum()
        }
        SpeedResult(singleMbps, mbps(total, t1))
    }
}
