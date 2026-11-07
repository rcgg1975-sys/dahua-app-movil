package es.dahua.porttester

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PortTesterApp(this) } }
    }
}

data class DeviceRow(val ip: String, val mac: String, val dns: String, val ports: String, val kind: String)
data class Probe(val state: String, val detail: String)

@Composable
fun PortTesterApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var lanIp by remember { mutableStateOf("192.168.1.190") }
    var lanPort1 by remember { mutableStateOf("37777") }
    var lanPort2 by remember { mutableStateOf("554") }
    var wanTarget by remember { mutableStateOf("") }
    var wanPort1 by remember { mutableStateOf("37777") }
    var wanPort2 by remember { mutableStateOf("554") }
    var publicIp by remember { mutableStateOf("") }
    var cidr by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Listo") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var currentIp by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf(listOf<DeviceRow>()) }
    val scope = rememberCoroutineScope()

    fun append(s: String) { output += s + "\n" }
    fun ports(a: String, b: String): List<Int> = listOf(a, b).filter { it.isNotBlank() }.map {
        it.toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: error("Puerto no válido: $it")
    }.distinct().also { require(it.isNotEmpty()) { "Introduce al menos un puerto" } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("DAHUA PORT TESTER", style = MaterialTheme.typography.headlineSmall)
        Text("Diagnóstico de red LAN / WAN", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            listOf("LAN", "WAN", "Escáner").forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. TEST DE RED LOCAL", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(lanIp, { lanIp = it }, label = { Text("IP local del grabador / equipo") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lanPort1, { lanPort1 = it }, label = { Text("Puerto 1") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(lanPort2, { lanPort2 = it }, label = { Text("Puerto 2") }, modifier = Modifier.weight(1f))
                }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        try {
                            val ps = ports(lanPort1, lanPort2); busy = true; status = "Comprobando LAN..."
                            for (p in ps) { val r = withContext(Dispatchers.IO) { tcpProbe(lanIp.trim(), p) }; append("LAN ${lanIp.trim()}:$p → ${r.state} (${r.detail})") }
                            status = "Prueba LAN finalizada"
                        } catch (e: Exception) { append("Error: ${e.message}"); status = "Error" } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("COMPROBAR LAN") }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; status = "Consultando IP pública..."
                        try { publicIp = withContext(Dispatchers.IO) { URL("https://api.ipify.org").readText().trim() }; append("IP WAN pública: $publicIp"); status = "IP pública actualizada" }
                        catch (e: Exception) { status = "No se pudo consultar la IP pública"; append(status) } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("OBTENER MI IP WAN PÚBLICA") }
                if (publicIp.isNotBlank()) Text("Tu IP pública: $publicIp")
            }
            1 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. TEST EXTERNO DESDE INTERNET", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(wanTarget, { wanTarget = it }, label = { Text("IP pública o DNS de destino") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(wanPort1, { wanPort1 = it }, label = { Text("Puerto 1") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(wanPort2, { wanPort2 = it }, label = { Text("Puerto 2") }, modifier = Modifier.weight(1f))
                }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        try {
                            val target = wanTarget.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
                            require(target.isNotBlank()) { "Introduce una IP o DNS de destino" }
                            val ps = ports(wanPort1, wanPort2); busy = true; status = "Solicitando prueba externa..."
                            for (p in ps) { val r = withContext(Dispatchers.IO) { externalProbe(target, p) }; append("WAN $target:$p → ${r.state}\n${r.detail}") }
                            status = "Prueba WAN finalizada"
                        } catch (e: Exception) { append("Error: ${e.message}"); status = "Error en prueba WAN" } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("COMPROBAR WAN EXTERNA") }
                Text("La prueba se realiza desde nodos externos de Check-Host; no desde tu propia Wi-Fi. Así se evita depender del NAT Loopback del router.", style = MaterialTheme.typography.bodySmall)
            }
            else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. ESCÁNER DE DISPOSITIVOS LAN", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(cidr, { cidr = it }, label = { Text("Rango CIDR (ej. 192.168.1.0/24)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { cidr = detectCidr(context) ?: "" }, modifier = Modifier.weight(1f)) { Text("DETECTAR RANGO") }
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            try {
                                val hosts = expandCidr(cidr.ifBlank { detectCidr(context) ?: error("No se detectó la red Wi-Fi; introduce el rango CIDR") })
                                cidr = cidr.ifBlank { detectCidr(context) ?: "" }
                                devices = emptyList(); progress = 0; total = hosts.size; currentIp = ""; busy = true; status = "Escaneando ${hosts.size} direcciones..."
                                val sem = Semaphore(32)
                                coroutineScope {
                                    hosts.map { ip -> async(Dispatchers.IO) {
                                        sem.withPermit {
                                            val row = scanHost(ip)
                                            withContext(Dispatchers.Main) {
                                                progress += 1; currentIp = ip
                                                if (row != null) devices = devices + row
                                            }
                                        }
                                    } }.awaitAll()
                                }
                                status = "Escaneo finalizado: ${devices.size} equipos encontrados"
                            } catch (e: Exception) { append("Error de escaneo: ${e.message}"); status = "Error" } finally { busy = false }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("ESCANEAR LAN") }
                }
                if (total > 0) {
                    LinearProgressIndicator(progress = { progress.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("IP $progress de $total · $currentIp · Faltan ${total - progress}", style = MaterialTheme.typography.bodySmall)
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { d -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                        Text(d.ip, style = MaterialTheme.typography.titleMedium)
                        Text("${d.kind} · Puertos: ${d.ports}")
                        if (d.mac.isNotBlank()) Text("MAC: ${d.mac}")
                        if (d.dns.isNotBlank()) Text("DNS: ${d.dns}")
                    } } }
                }
                Text("Nota: Android puede restringir la lectura de direcciones MAC. Algunos equipos bloquean ping y pueden no aparecer; la identificación Dahua es orientativa.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.labelMedium)
        if (output.isNotBlank()) {
            Text("RESULTADOS", style = MaterialTheme.typography.titleSmall)
            Text(output.takeLast(2200), modifier = Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun tcpProbe(host: String, port: Int): Probe = try {
    Socket().use { it.connect(InetSocketAddress(host, port), 3000) }
    Probe("ABIERTO", "Conexión TCP establecida")
} catch (e: Exception) { Probe("CERRADO / SIN RESPUESTA", e.message ?: "No se pudo conectar") }

private fun externalProbe(host: String, port: Int): Probe {
    val encoded = java.net.URLEncoder.encode("$host:$port", "UTF-8")
    val headers = mapOf("Accept" to "application/json", "User-Agent" to "Dahua-Port-Tester-Android/1.0")
    val start = requestJson("https://check-host.net/check-tcp?host=$encoded&max_nodes=3", headers)
    val id = start.optString("request_id")
    if (id.isBlank()) return Probe("INDETERMINADO", "Check-Host no devolvió identificador")
    val link = start.optString("permanent_link")
    var result = JSONObject()
    for (attempt in 0 until 12) {
        Thread.sleep(2000)
        result = requestJson("https://check-host.net/check-result/$id", headers)
        if (result.keys().asSequence().any { key -> !result.isNull(key) }) break
    }
    var ok = 0; var fail = 0
    val details = mutableListOf<String>()
    val keys = result.keys()
    while (keys.hasNext()) {
        val key = keys.next(); val value = result.optJSONArray(key)
        if (value == null) continue
        val first = value.optJSONObject(0)
        if (first != null && first.has("time")) { ok++; details += "$key: conectado" }
        else { fail++; details += "$key: sin conexión" }
    }
    return when { ok > 0 -> Probe("ABIERTO", "Responde desde $ok nodo(s). ${details.joinToString(" | ")} $link")
        fail > 0 -> Probe("CERRADO / NO ACCESIBLE", "Ningún nodo conectó. ${details.joinToString(" | ")} $link")
        else -> Probe("INDETERMINADO", "Sin resultados concluyentes. $link") }
}

private fun requestJson(url: String, headers: Map<String, String>): JSONObject {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 15000; headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    return try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
}

private fun scanHost(ip: String): DeviceRow? {
    val ports = listOf(80, 443, 554, 37777, 37778, 8000, 8080, 23, 22)
    val open = ports.filter { p -> try { Socket().use { it.connect(InetSocketAddress(ip, p), 240) }; true } catch (_: Exception) { false } }
    val ping = try { InetAddress.getByName(ip).isReachable(350) } catch (_: Exception) { false }
    if (!ping && open.isEmpty()) return null
    val kind = when { 37777 in open || 37778 in open -> "POSIBLE DAHUA"; 554 in open -> "Equipo con RTSP"; 80 in open || 8080 in open || 8000 in open -> "Equipo con interfaz web"; else -> "Dispositivo IP" }
    val dns = try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } ?: "" } catch (_: Exception) { "" }
    val mac = readMac(ip)
    return DeviceRow(ip, mac, dns, open.joinToString(", ").ifBlank { "No detectados" }, kind)
}

private fun readMac(ip: String): String = try {
    java.io.File("/proc/net/arp").readLines().drop(1).firstOrNull { it.trim().startsWith("$ip ") }?.split(Regex("\\s+"))?.getOrNull(3)?.uppercase(Locale.ROOT) ?: ""
} catch (_: Exception) { "" }

private fun detectCidr(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps: NetworkCapabilities = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val lp: LinkProperties = cm.getLinkProperties(network) ?: return null
        val link = lp.linkAddresses.firstOrNull { it.address is java.net.Inet4Address } ?: return null
        val ip = link.address.hostAddress ?: return null
        val prefix = link.prefixLength
        val bytes = InetAddress.getByName(ip).address
        val value = ((bytes[0].toLong() and 255) shl 24) or ((bytes[1].toLong() and 255) shl 16) or ((bytes[2].toLong() and 255) shl 8) or (bytes[3].toLong() and 255)
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val networkValue = value and mask
        val net = listOf(24, 16, 8, 0).joinToString(".") { ((networkValue ushr it) and 255).toString() }
        "$net/$prefix"
    } catch (_: Exception) { null }
}

private fun expandCidr(cidr: String): List<String> {
    val parts = cidr.trim().split("/"); require(parts.size == 2) { "Formato CIDR no válido" }
    val prefix = parts[1].toIntOrNull() ?: error("Prefijo no válido")
    require(prefix in 16..30) { "El prefijo debe estar entre /16 y /30" }
    val bytes = InetAddress.getByName(parts[0]).address; require(bytes.size == 4) { "Solo se admite IPv4" }
    val value = ((bytes[0].toLong() and 255) shl 24) or ((bytes[1].toLong() and 255) shl 16) or ((bytes[2].toLong() and 255) shl 8) or (bytes[3].toLong() and 255)
    val block = 1L shl (32 - prefix); val network = (value / block) * block
    val count = block - 2; require(count <= 4094) { "La red supera el límite de 4094 equipos" }
    return (1L until block - 1).map { n -> val v = network + n; listOf(24,16,8,0).joinToString(".") { ((v ushr it) and 255).toString() } }
}
