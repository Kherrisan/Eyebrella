package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.GET
import cn.kherrisan.eyebrella.core.common.ungzip
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import cn.kherrisan.eyebrella.core.websocket.AuthenticatableWebsocketDispatcher
import cn.kherrisan.eyebrella.core.websocket.DefaultSubscription
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class HuobiSpotTradingWebsocketDispatcher @Autowired constructor(
        staticConfiguration: HuobiStaticConfiguration,
        runtimeConfig: HuobiRuntimeConfig
) : HuobiSpotMarketWebsocketDispatcher(staticConfiguration, runtimeConfig), AuthenticatableWebsocketDispatcher {

    override val authenticationService = HuobiAuthenticateService(staticConfiguration.spotTradingWsHost)
    override var isAuthenticated: Boolean = false
    override var authenticationPromise: Promise<Any> = Promise.promise()
    override val host: String = staticConfiguration.spotTradingWsHost

    override suspend fun dispatch(bytes: ByteArray) {
        val clear = ungzip(bytes)
        logger.debug(clear)
        val obj = JsonParser.parseString(clear).asJsonObject
        val op = obj["op"].asString
        when (op) {
            "ping" -> {
                //ping-pong
                obj.remove("op")
                obj.addProperty("op", "pong")
                send(Gson().toJson(obj))
            }
            "auth" -> {
                //鉴权的响应
                //鉴权成功的响应格式：{"op":"auth","ts":1581497756963,"err-code":0,"data":{"user-id":5691027}}
                logger.debug(obj)
                //ch:"auth"
                if (obj["err-code"].asInt == 0) {
                    //鉴权成功
                    unregister("auth")
                    triggerAuthenticationEvent()
                }
            }
            "sub" -> {
                //订阅的响应
                triggerSubscribedEvent(obj["topic"].asString)
            }
            "unsub" -> {
                //取消订阅的响应
                triggerUnsubscribedEvent(obj["topic"].asString)
            }
            "notify" -> {
                //推送的数据
                val ch = obj["topic"].asString
                val sub = defaultSubscriptionMap[ch] as DefaultSubscription
                sub.resolver(obj, sub)
            }
        }
    }

    override suspend fun authenticate() {
        val op = "auth"
        val subscription = newSubscription<Any>(op)
        val params = mutableMapOf<String, Any>()
        authenticationService.signWebsocketRequest(GET, "/ws/v1", params)
        params["op"] = op
        subscription.requestPacket = {
            Gson().toJson(params)
        }
        logger.debug("Start to authenticate $host")
        send(subscription.requestPacket())
        authenticationPromise.future().await()
        logger.debug("Authentication for $host success")
    }

    override fun <T : Any> newSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit): DefaultSubscription<T> {
        val subscription = super.newSubscription(channel, resolver)
        subscription.requestPacket = {
            Gson().toJson(mutableMapOf(
                    "op" to "req",
                    "topic" to channel
            ))
        }
        subscription.subPacket = {
            Gson().toJson(mutableMapOf(
                    "op" to "sub",
                    "topic" to channel
            ))
        }
        subscription.unsubPacket = {
            Gson().toJson(mutableMapOf(
                    "op" to "unsub",
                    "topic" to channel
            ))
        }
        return subscription
    }

    override fun newChildDispatcher(): AbstractWebsocketDispatcher {
        val d = HuobiSpotTradingWebsocketDispatcher(staticConfig, runtimeConfig as HuobiRuntimeConfig)
        childDispatcher.add(d)
        return d
    }

    override suspend fun reconfigure() {
        super.reconfigure()
        isAuthenticated = false
    }
}