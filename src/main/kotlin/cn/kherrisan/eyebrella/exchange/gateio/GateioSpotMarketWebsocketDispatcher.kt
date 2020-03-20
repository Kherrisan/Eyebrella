package cn.kherrisan.eyebrella.exchange.gateio

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.stringId
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import cn.kherrisan.eyebrella.core.websocket.DefaultSubscription
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
open class GateioSpotMarketWebsocketDispatcher @Autowired constructor(
        staticConfiguration: GateioStaticConfiguration,
        runtimeConfig: GateioRuntimeConfig
) : AbstractWebsocketDispatcher(runtimeConfig) {

    val idMap = HashMap<Int, String>()
    override val host: String = staticConfiguration.spotMarketWsHost
    override val name: ExchangeName = ExchangeName.GATEIO

    override suspend fun handleCommandResponse(elem: JsonElement) {
        val obj = elem.asJsonObject
        val ch = idMap[obj["id"].asInt]!!
        idMap.remove(obj["id"].asInt)
        if (!defaultSubscriptionMap[ch]!!.isSubscribed) {
            triggerSubscribedEvent(ch)
        } else {
            triggerUnsubscribedEvent(ch)
        }
    }

    override suspend fun dispatch(bytes: ByteArray) {
        val obj = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).asJsonObject
        if (obj.has("result")) {
            // sub and unsub event
            handleCommandResponse(obj)
        } else {
            val m = obj["method"].asString.removeSuffix(".update")
            val sym: String
            sym = when {
                m.contains("depth") -> {
                    // 我也不知道为什么gateio的response格式这么奇葩。。。
                    obj["params"].asJsonArray.last().asString
                }
                m.contains("kline") -> {
                    obj["params"].asJsonArray[0].asJsonArray.last().asString
                }
                else -> {
                    obj["params"].asJsonArray[0].asString
                }
            }
            val ch = "$m:$sym"
            val sub = defaultSubscriptionMap[ch] as DefaultSubscription
            sub.resolver(obj, sub)
        }
    }

    override fun <T : Any> newSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit): DefaultSubscription<T> {
        val subscription = super.newSubscription(channel, resolver)
        val comm = channel.indexOf(":")
        // 这里的channel并不是真正的channel
        val method = channel.substring(0, comm)
        val params = channel.substring(comm + 1)
        val args = JsonParser.parseString(params).asJsonArray
        // 真正的channel——ticker:$symbol
        val ch = "$method:${args[0].asString}"
        subscription.subPacket = {
            val id = stringId().toInt()
            idMap[id] = ch
            Gson().toJson(mapOf(
                    "id" to id,
                    "method" to "$method.subscribe",
                    "params" to args
            ))
        }
        subscription.unsubPacket = {
            val id = stringId().toInt()
            idMap[id] = ch
            Gson().toJson(mapOf(
                    "id" to id,
                    "method" to "$method.unsubscribe",
                    "params" to args
            ))
        }
        return subscription
    }
}