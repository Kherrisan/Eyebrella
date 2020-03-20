package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.SubscriptionHandler
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ServiceDataAdaptor
import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import cn.kherrisan.eyebrella.entity.BBO
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.concurrent.ScheduledFuture
import org.apache.logging.log4j.LogManager
import java.util.concurrent.TimeUnit

class OkexMarketMessageDecoder() : MessageToMessageDecoder<JsonObject>()
    , ServiceDataAdaptor by SpringContainer[OkexServiceDataAdaptor::class] {

    override fun decode(ctx: ChannelHandlerContext?, msg: JsonObject?, out: MutableList<Any>?) {
        msg!!
        when {
            msg.has("event") -> {
                val event = msg["event"].asString
                when (event) {
                    "subscribe" -> out!!.add(Gson().fromJson(msg, OkexMarketSubscriptionResponse::class.java))
                    "unsubscribe" -> {
                        //暂时不处理取消订阅的情况
                    }
                }
            }
            else -> {
                val table = msg["table"].asString
                when {
                    table.contains("ticker") -> parseTicker(msg, out!!)
                    else -> {

                    }
                }
            }
        }
    }

    private fun parseTicker(obj: JsonObject, out: MutableList<Any>) {
        obj["data"].asJsonArray.map { it.asJsonObject }
            .forEach {
                val symbol = symbol(it["instrument_id"])
                out.add(
                    BBO(
                        symbol,
                        ExchangeName.OKEX,
                        price(it["best_ask"], symbol),
                        size(it["best_ask_size"], symbol),
                        price(it["best_bid"], symbol),
                        size(it["best_bid_size"], symbol),
                        date(it["timestamp"])
                    )
                )
            }
    }
}

class OkexMarketSubscriptionHandler :
    SubscriptionHandler<OkexMarketSubscriptionResponse, OkexMarketSubscriptionRequest>() {

    override fun isSubscriptionSuccess(msg: OkexMarketSubscriptionResponse): Boolean {
        return msg.event != "error"
    }

    override fun requestId(req: OkexMarketSubscriptionRequest): List<String> {
        return req.args
    }

    override fun responseId(resp: OkexMarketSubscriptionResponse): List<String> {
        return listOf(resp.channel)
    }
}

class OkexPingPongHandler : SimpleChannelInboundHandler<String>() {

    private val logger = LogManager.getLogger()
    private var receivedPongWithinPeriod = true
    private var scheduleFuture: ScheduledFuture<*>? = null
    private val PING_PONG_PERIOD = 20L

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: String?) {
        if (scheduleFuture != null && scheduleFuture!!.isCancellable) {
            scheduleFuture!!.cancel(true)
        }
        //每次收到消息之后，都会启动一个计时器，在 30s 后检查有没有该 30s 内有没有收到消息
        scheduleFuture = ctx!!.channel().eventLoop().schedule({
            ctx.writeAndFlush(TextWebSocketFrame("ping"))
            receivedPongWithinPeriod = false
            ctx.channel().eventLoop().schedule({
                if (!receivedPongWithinPeriod) {
                    ctx.channel().disconnect()
                }
            }, PING_PONG_PERIOD, TimeUnit.SECONDS)
        }, PING_PONG_PERIOD, TimeUnit.SECONDS)
        if (msg == "pong") {
            receivedPongWithinPeriod = true
        } else {
            ctx.fireChannelRead(msg)
        }
    }
}