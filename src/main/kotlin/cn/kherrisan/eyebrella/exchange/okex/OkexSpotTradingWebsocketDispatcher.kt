package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.GET
import cn.kherrisan.eyebrella.core.common.d64ungzip
import cn.kherrisan.eyebrella.core.websocket.AuthenticatableWebsocketDispatcher
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class OkexSpotTradingWebsocketDispatcher @Autowired constructor(
        staticConfiguration: OkexStaticConfiguration,
        runtimeConfig: OkexRuntimeConfig
) : OkexSpotMarketWebsocketDispatcher(staticConfiguration, runtimeConfig), AuthenticatableWebsocketDispatcher {

    override val authenticationService = OkexAuthenticateService(staticConfiguration.spotTradingWsHost)
    override var isAuthenticated: Boolean = false
    override var authenticationPromise = Promise.promise<Any>()
    override val host: String = staticConfiguration.spotTradingWsHost
    override val name: ExchangeName = ExchangeName.OKEX

    override suspend fun dispatch(bytes: ByteArray) {
        val clear = d64ungzip(bytes)
        if (clear == "pong") {
            return
        }
        val obj = JsonParser.parseString(clear).asJsonObject
        if (obj.has("event") && obj["event"].asString == "login") {
            //auth event
            if (obj["success"].asBoolean) {
                unregister("login")
                triggerAuthenticationEvent()
            } else {
                logger.error(obj)
            }
        } else {
            super.dispatch(bytes)
        }
    }

    override suspend fun authenticate() {
        val subscription = newSubscription<Any>("login")
        val params = mutableMapOf<String, Any>()
        authenticationService.signWebsocketRequest(GET, "/users/self/verify", params)
        subscription.requestPacket = {
            Gson().toJson(mapOf(
                    "op" to "login",
                    "args" to listOf(
                            params["api_key"], params["passphrase"], params["timestamp"], params["sign"]
                    )
            ))
        }
        logger.debug("Start to authenticate $host")
        send(subscription.requestPacket())
        authenticationPromise.future().await()
        logger.debug("Authentication for $host success")
        authenticationPromise.future().await()
    }
}