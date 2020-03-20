package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.stringId
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import cn.kherrisan.eyebrella.core.websocket.DefaultSubscription
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.kotlin.coroutines.awaitEvent
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class KucoinSpotMarketWebsocketDispatcher @Autowired constructor(runtimeConfig: KucoinRuntimeConfig) : AbstractWebsocketDispatcher(runtimeConfig) {

    override val host: String = ""
    override val name: ExchangeName = ExchangeName.KUCOIN

    val idMap = HashMap<Int, String>()

    private suspend fun resetPingTimer() {
        launch(vertx.dispatcher()) {
            awaitEvent<Long> { vertx.setTimer(runtimeConfig.pingInterval!!.toLong(), it) }
            send(Gson().toJson(mapOf("id" to stringId(), "type" to "ping")))
        }
    }

    override suspend fun dispatch(bytes: ByteArray) {
        val clear = bytes.toString(StandardCharsets.UTF_8)
        val obj = JsonParser.parseString(clear).asJsonObject
        val type = obj["type"].asString
        when (type) {
            "pong" -> resetPingTimer()
            "ack" -> {
                val ch = idMap[obj["id"].asString.toInt()]!!
                if (defaultSubscriptionMap[ch]!!.isSubscribed) {
                    triggerUnsubscribedEvent(ch)
                } else {
                    triggerSubscribedEvent(ch)
                }
                idMap.remove(obj["id"].asString.toInt())
            }
            "message" -> {
                val ch = obj["topic"].asString
                val sub = defaultSubscriptionMap[ch]!! as DefaultSubscription
                sub.resolver(obj, sub)
            }
        }
    }

    override fun <T : Any> newSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit): DefaultSubscription<T> {
        val subscription = super.newSubscription(channel, resolver)
        subscription.subPacket = {
            val id = stringId().toInt()
            idMap[id] = channel
            Gson().toJson(mapOf("id" to id,
                    "type" to "subscribe",
                    "response" to true,
                    "topic" to channel))
        }
        subscription.unsubPacket = {
            val id = stringId().toInt()
            idMap[id] = channel
            Gson().toJson(mapOf("id" to id,
                    "type" to "unsubscribe",
                    "response" to true,
                    "topic" to channel))
        }
        return subscription
    }
}