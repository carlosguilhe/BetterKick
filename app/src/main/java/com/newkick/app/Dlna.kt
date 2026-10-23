package com.newkick.app

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/** Uma TV (ou caixa de som) da rede que aceita receber vídeo por DLNA/UPnP. */
data class Renderer(val name: String, val controlUrl: String)

/**
 * Transmissão por DLNA/UPnP, sem serviços do Google: acha as TVs na rede (SSDP), manda o endereço
 * da live para a TV (AVTransport) e ela mesma baixa o vídeo. O celular só controla.
 */
object Dlna {
    private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Procura por alguns segundos e devolve as TVs achadas (sem repetir). */
    suspend fun discover(context: Context, windowMs: Long = 3_500): List<Renderer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("betterkick-dlna")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 400
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) {
                    listOf("urn:schemas-upnp-org:device:MediaRenderer:1", SERVICE).forEach { target ->
                        val message = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n"
                            .toByteArray()
                        runCatching { socket.send(DatagramPacket(message, message.size, group, 1900)) }
                    }
                }
                val buffer = ByteArray(4096)
                val end = System.currentTimeMillis() + windowMs
                while (System.currentTimeMillis() < end) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)?.let { locations += it }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
        locations.mapNotNull { describe(it) }.distinctBy { it.controlUrl }
    }

    /** Lê a descrição da TV: nome amigável e o endereço de controle do AVTransport. */
    private fun describe(location: String): Renderer? = runCatching {
        val xml = http.newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() } ?: return null
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        var name: String? = null
        var control: String? = null
        var type: String? = null
        var ctl: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "friendlyName" -> if (name == null) name = parser.nextText().trim()
                    "serviceType" -> type = parser.nextText().trim()
                    "controlURL" -> ctl = parser.nextText().trim()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "service") {
                if (type?.contains("AVTransport") == true && ctl != null && control == null) control = ctl
                type = null
                ctl = null
            }
            event = parser.next()
        }
        val target = control ?: return null
        Renderer(name ?: "TV", URI(location).resolve(target).toString())
    }.getOrNull()

    /** Manda a live para a TV e dá play. Lança erro se a TV recusar. */
    suspend fun play(renderer: Renderer, url: String, title: String) = withContext(Dispatchers.IO) {
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:application/vnd.apple.mpegurl:*\">${escape(url)}</res></item></DIDL-Lite>"
        soap(renderer, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${escape(url)}</CurrentURI><CurrentURIMetaData>${escape(didl)}</CurrentURIMetaData>")
        soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun stop(renderer: Renderer) = withContext(Dispatchers.IO) {
        runCatching { soap(renderer, "Stop", "<InstanceID>0</InstanceID>") }
    }

    private fun soap(renderer: Renderer, action: String, body: String) {
        val envelope = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$SERVICE\">$body</u:$action></s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(renderer.controlUrl)
            .header("SOAPAction", "\"$SERVICE#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { if (!it.isSuccessful) error("TV respondeu ${it.code}") }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}

/** Qual TV está recebendo a live agora (para mostrar "Parar transmissão"). */
object DlnaCast {
    var renderer by mutableStateOf<Renderer?>(null)

    suspend fun stop() {
        renderer?.let { Dlna.stop(it) }
        renderer = null
    }
}

/** Lista as TVs da rede e manda a live para a escolhida. */
@Composable
fun CastDialog(streamUrl: String, title: String, onStarted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<Renderer>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        devices = null
        devices = runCatching { Dlna.discover(context) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Transmitir para a TV") },
        text = {
            Column {
                when (val list = devices) {
                    null -> Text("Procurando TVs na rede…", fontSize = 14.sp)
                    else -> if (list.isEmpty()) {
                        Text("Nenhuma TV encontrada. A TV precisa estar ligada, no mesmo Wi-Fi do celular e aceitar DLNA.", fontSize = 14.sp)
                    } else {
                        list.forEach { device ->
                            Text(
                                device.name,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        busy = true
                                        scope.launch {
                                            val ok = runCatching { Dlna.play(device, streamUrl, title) }.isSuccess
                                            busy = false
                                            if (ok) {
                                                DlnaCast.renderer = device
                                                onStarted()
                                                onDismiss()
                                            } else {
                                                Toast.makeText(context, "A TV não aceitou a transmissão.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { attempt++ }) { Text("Procurar de novo") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
