package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.d64ungzip
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import cn.kherrisan.eyebrella.core.websocket.DefaultSubscription
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

const val PING_PERIOD = 20_1000L

@Component
open class OkexSpotMarketWebsocketDispatcher(
        val staticConfiguration: OkexStaticConfiguration,
        runtimeConfig: OkexRuntimeConfig
) : AbstractWebsocketDispatcher(runtimeConfig) {

    override val host: String = staticConfiguration.spotMarketWsHost
    override val name: ExchangeName = ExchangeName.OKEX
    private var receivedInPeriod = false

    fun resetPingTimer() {
        launch(vertx.dispatcher()) {
            receivedInPeriod = false
            delay(PING_PERIOD)
            if (!receivedInPeriod) {
                send("ping")
                delay(PING_PERIOD)
                if (!receivedInPeriod) {
                    reconnect()
                }
            }
        }
    }

    override suspend fun dispatch(bytes: ByteArray) {
        receivedInPeriod = true
        val clear = d64ungzip(bytes)
        if (clear == "pong") {
            return
        }
        val obj = JsonParser.parseString(clear).asJsonObject
        if (obj.has("event")) {
            // subscribe event
            val evt = obj["event"].asString
            val ch = obj["channel"].asString
            if (evt == "subscribe") {
                triggerSubscribedEvent(ch)
            } else {
                triggerUnsubscribedEvent(ch)
            }
        } else {
            val table = obj["table"].asString
            obj["data"].asJsonArray
                    .map { it.asJsonObject["instrument_id"].asString }
                    .map { "$table:$it" }
                    .forEach {
                        // deliver the data
                        val sub = defaultSubscriptionMap[it] as DefaultSubscription
                        sub.resolver(obj, sub)
                    }
        }
        resetPingTimer()
    }

    override fun <T : Any> newSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit): DefaultSubscription<T> {
        val subscription = super.newSubscription(channel, resolver)
        subscription.subPacket = { Gson().toJson(mapOf("op" to "subscribe", "args" to listOf(channel))) }
        subscription.unsubPacket = { Gson().toJson(mapOf("op" to "unsubscribe", "args" to listOf(channel))) }
        return subscription
    }
}