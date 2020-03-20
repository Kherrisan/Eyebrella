package cn.kherrisan.eyebrella.core.websocket

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.MarketDataListener
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.readableSize
import cn.kherrisan.eyebrella.entity.BBO
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.vertx.core.Promise
import org.apache.commons.lang3.time.DateUtils
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.lang.RuntimeException
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import kotlin.collections.HashMap


class WebsocketHandshakeListener(private val channelInitializer: AbstractChannelInitializer) :
    ChannelInboundHandlerAdapter() {
    private val logger = LogManager.getLogger()
    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            logger.debug("${ctx!!.channel().localAddress()} 握手 ${ctx.channel().remoteAddress()} 成功")
            channelInitializer.onHandshakeComplete(ctx)
            channelInitializer.handshakePromise.complete(ctx.channel())
        }
    }
}

class JsonCodec : MessageToMessageCodec<String, Any>() {

    private val logger = LogManager.getLogger()

    override fun encode(ctx: ChannelHandlerContext?, msg: Any?, out: MutableList<Any>?) {
        logger.trace(msg)
        out!!.add(TextWebSocketFrame(Gson().toJson(msg)))
    }

    override fun decode(ctx: ChannelHandlerContext?, msg: String?, out: MutableList<Any>?) {
        logger.trace(msg)
        out!!.add(JsonParser.parseString(msg).asJsonObject)
    }
}

class GzipDecompressor(val deflat: Boolean = false) : MessageToMessageDecoder<WebSocketFrame>() {

    private val logger = LogManager.getLogger()

    private fun ungzip(byteArray: ByteArray): String {
        val bis = ByteArrayInputStream(byteArray)
        val gis = GZIPInputStream(bis)
        return gis.readAllBytes().toString(StandardCharsets.UTF_8)
    }

    private fun d64ungzip(byteArray: ByteArray): String {
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

    override fun decode(ctx: ChannelHandlerContext?, msg: WebSocketFrame?, out: MutableList<Any>?) {
        val bytes = ByteArray(msg!!.content().readableBytes())
        msg.content().readBytes(bytes)
        val clear = if (deflat) {
            d64ungzip(bytes)
        } else {
            ungzip(bytes)
        }
        out!!.add(clear)
    }
}

class DataStatisticsHandler : ChannelInboundHandlerAdapter() {

    private val staticticsMap: MutableMap<SocketAddress, Long> = HashMap()
    private val total: Long = 0
    private var lastLogTime: Date = MyDate()
    private val LOG_INTERVAL_MS = 10000
    private val logger = LogManager.getLogger()

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val addr = ctx!!.channel().remoteAddress()
        val byteBuf = msg!! as ByteBuf
        val current = staticticsMap.getOrPut(addr) { 0L }
        staticticsMap.put(addr, current + byteBuf.readableBytes())
        if (DateUtils.addMilliseconds(lastLogTime, LOG_INTERVAL_MS) < Date()) {
            staticticsMap.forEach { t, u ->
                logger.debug("\t$t\t${readableSize(u)}")
            }
            lastLogTime = Date()
        }
        ctx.fireChannelRead(msg)
    }
}

class AutoReconnectingHandler(
    val client: WebsocketClient,
    val spot: AbstractSpot
) : ChannelInboundHandlerAdapter() {

    private val logger = LogManager.getLogger()

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        logger.error("断线，正在重新连接 ${ctx!!.channel().remoteAddress()} 并订阅原有频道")
        ctx.pipeline().remove(this)
        ctx.channel().eventLoop().schedule({
            client.retrials += 1
            client.connectToRemote()
        }, 3L, TimeUnit.SECONDS)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        logger.error(cause)
        cause!!.printStackTrace()
    }
}

interface SubscriptionRequest

interface SubscriptionResponse

abstract class AbstractSubscriptionHandler(val name: String) :
    MessageToMessageCodec<SubscriptionResponse, SubscriptionRequest>() {

    private val logger = LogManager.getLogger()
    var subscriptionPromise: Promise<Any>? = null
    var retrals = 0

    override fun encode(ctx: ChannelHandlerContext?, msg: SubscriptionRequest?, out: MutableList<Any>?) {
        subscriptionPromise = Promise.promise()
        ctx!!.channel().eventLoop().schedule({
            //如果订阅没有成功，自动重发订阅请求
            if (!subscriptionPromise!!.future().succeeded()) {
                if (retrals >= 3) {
                    throw RuntimeException(subscriptionPromise!!.future().cause())
                }
                retrals += 1
                logger.debug("订阅 $name 失败，尝试重新订阅（${retrals}）。")
                ctx.pipeline().writeAndFlush(msg)
            }
        }, 10, TimeUnit.SECONDS)
        ctx.writeAndFlush(msg)
    }

    override fun decode(ctx: ChannelHandlerContext?, msg: SubscriptionResponse?, out: MutableList<Any>?) {
        if (checkSubscriptionSuccess(msg!!)) {
            logger.debug("订阅 $name 成功。")
            subscriptionPromise!!.complete()
            ctx!!.pipeline().remove(this)
        } else {
            subscriptionPromise!!.fail(msg.toString())
        }
    }

    abstract fun checkSubscriptionSuccess(resp: SubscriptionResponse): Boolean
}

class ExchangeBBOHandler(val listener: MarketDataListener) : SimpleChannelInboundHandler<BBO>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: BBO?) {
        listener.updateBBO(msg!!)
    }
}