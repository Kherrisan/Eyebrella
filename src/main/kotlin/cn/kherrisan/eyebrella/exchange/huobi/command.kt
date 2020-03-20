package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.stringId
import cn.kherrisan.eyebrella.core.websocket.SubscriptionRequest
import cn.kherrisan.eyebrella.core.websocket.SubscriptionResponse
import com.google.gson.annotations.SerializedName
import io.vertx.core.Promise

data class HuobiTradingPing(
    var op: String,
    val ts: Long
)

data class HuobiPing(
    val ping: Long
)

data class HuobiPong(
    val pong: Long
)

data class HuobiMarketRequest(
    val req: String,
    val id: String = stringId()
)

data class HuobiMarketSubscriptionRequest(
    val sub: String,
    val id: String = stringId()
) : SubscriptionRequest

data class HuobiMarketSusbcriptionPromisingRequest(
    val sub: String,
    val id: String = stringId(),
    val promise: Promise<Any> = Promise.promise()
) : SubscriptionRequest

data class HuobiMarketSubscriptionResponse(
    val id: String = stringId(),
    val status: String,
    val subbed: String,
    val ts: Long
) : SubscriptionResponse

data class HuobiTradingSubscriptionRequest(
    val op: String,
    val topic: String,
    val model: String = "0",
    val cid: String = stringId()
)

data class HuobiTradingSubscriptionResponse(
    val op: String,
    val topic: String,
    val cid: String,
    @SerializedName("error-code")
    val error_code: Int,
    val ts: Long
)

data class HuobiAuthenticationResponse(
    val op: String,
    @SerializedName("error-code")
    val error_code: Int,
    val ts: Long
)
