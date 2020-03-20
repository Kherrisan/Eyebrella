package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.entity.Depth
import cn.kherrisan.eyebrella.entity.DepthItem
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.internal.bind.util.ISO8601Utils
import kotlinx.coroutines.delay
import org.apache.commons.codec.binary.Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

fun hmacSHA256Signature(content: String, secret: String): ByteArray {
    val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keySpec)
    return mac.doFinal(content.toByteArray())
}

fun hmacSHA512Signature(content: String, secret: String): ByteArray {
    val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA512")
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(keySpec)
    return mac.doFinal(content.toByteArray())
}

fun sortedUrlEncode(params: Map<String, Any>?): String {
    if (params == null || params.isEmpty())
        return ""
    return params.keys.stream()
        .map { key ->
            val encoded = URLEncoder.encode(params[key].toString(), StandardCharsets.UTF_8.toString())
            "$key=$encoded"
        }
        .sorted()
        .collect(Collectors.joining("&"))
}

fun urlEncode(params: Map<String, Any>?): String {
    if (params == null || params.isEmpty())
        return ""
    return params.keys.stream()
        .map { key ->
            val encoded = URLEncoder.encode(params[key].toString(), StandardCharsets.UTF_8.toString())
            "$key=$encoded"
        }
        .collect(Collectors.joining("&"))
}

val DTT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

val DT_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

val D_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd")

fun gmt(): String = Instant.ofEpochSecond(Instant.now().epochSecond).atZone(ZoneId.of("Z")).format(DTT_FORMAT)

fun iso8601WithMS(): String = ISO8601Utils.format(Date(), true)

fun encodedParamsToUrl(url: String, params: Map<String, Any>): String {
    return "$url?${sortedUrlEncode(params)}"
}

val RANDOM = Random(System.currentTimeMillis())

var id = RANDOM.nextInt(100)

fun stringId(): String = "${id++}"

fun uuid(): String {
    return UUID.randomUUID().toString()
}

fun intId(): Int = id++

fun id(): Long = (id++).toLong()

fun gzip(text: String): String {
    val bos = ByteArrayOutputStream(text.length)
    val gos = GZIPOutputStream(bos)
    gos.write(text.toByteArray(StandardCharsets.UTF_8))
    gos.close()
    return bos.toString(StandardCharsets.UTF_8)
}

fun ungzip(byteArray: ByteArray): String {
    val bis = ByteArrayInputStream(byteArray)
    val gis = GZIPInputStream(bis)
    return gis.readAllBytes().toString(StandardCharsets.UTF_8)
}

fun d64ungzip(byteArray: ByteArray): String {
    val appender = StringBuilder()
    try {
        val infl = Inflater(true)
        infl.setInput(byteArray, 0, byteArray.size)
        val result = ByteArray(1024)
        while (!infl.finished()) {
            val length = infl.inflate(result)
            appender.append(String(result, 0, length, StandardCharsets.UTF_8))
        }
        infl.end()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return appender.toString()
}

val MD5_DG = MessageDigest.getInstance("MD5")

fun fullMD5(text: String): String {
    return Hex.encodeHexString(MD5_DG.digest(text.toByteArray()))
}

fun md5(text: String, prefix: Int = 6): String {
    return fullMD5(text).substring(0, prefix)
}

class MyDate(ts: Long) : Date(ts) {

    companion object {
        val BEIJING_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        init {
            BEIJING_TIME_FORMAT.timeZone = TimeZone.getTimeZone("GMT+8:00")
        }
    }

    constructor() : this(System.currentTimeMillis())

    override fun toString(): String {
        return BEIJING_TIME_FORMAT.format(this)
    }
}

fun parseDepthN(symbol: Symbol, obj: JsonObject, adaptor: ServiceDataAdaptor): Depth {
    val asks = mutableListOf<DepthItem>()
    obj["asks"]?.asJsonArray?.map { it.asJsonArray }
        ?.forEach { asks.add(DepthItem(adaptor.price(it[0], symbol), adaptor.size(it[1], symbol))) }
    val bids = mutableListOf<DepthItem>()
    obj["bids"]?.asJsonArray?.map { it.asJsonArray }
        ?.forEach { bids.add(DepthItem(adaptor.price(it[0], symbol), adaptor.size(it[1], symbol))) }
    asks.sortDescending()
    bids.sortDescending()
    return Depth(symbol, MyDate(), asks, bids)
}

fun parseDepth(symbol: Symbol, obj: JsonObject, decimal: (JsonElement) -> BigDecimal): Depth {
    val askMap = ConcurrentHashMap<BigDecimal, BigDecimal>()
    for (i in obj["asks"].asJsonArray) {
        askMap[decimal(i.asJsonArray[0])] = decimal(i.asJsonArray[1])
    }
    val bidMap = ConcurrentHashMap<BigDecimal, BigDecimal>()
    for (i in obj["bids"].asJsonArray) {
        bidMap[decimal(i.asJsonArray[0])] = decimal(i.asJsonArray[1])
    }
    return Depth(symbol, MyDate(), askMap, bidMap)
}

/**
 * 合并两个深度
 *
 * 将change合并到base上
 *
 * @param base Depth
 * @param change Depth
 */
fun mergeDepth(base: Depth, change: Depth) {
    mergeDepthList(base.asks, change.asks)
    base.asks.sortDescending()
    mergeDepthList(base.bids, change.bids)
    base.bids.sort()
}

fun mergeDepthList(baseList: MutableList<DepthItem>, changeList: List<DepthItem>) {
    var bc = 0
    for (change in changeList) {

    }
}

fun objSimpName(obj: Any?): String = "${obj?.javaClass?.simpleName}@${obj.hashCode()}"

suspend fun randomDelay(upbound: Int = 2000) {
    delay(RANDOM.nextInt(upbound).toLong())
}