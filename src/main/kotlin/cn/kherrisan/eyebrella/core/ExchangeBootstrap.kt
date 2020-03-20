package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.MessageToMessageCodec
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.TimeUnit

abstract class SubscriptionHandler<RESP, REQ> : MessageToMessageCodec<RESP, REQ>() {

    protected val idPromiseMap = mutableMapOf<String, Promise<RESP>>()
    protected val logger = LogManager.getLogger()
    private val hasLoggedSet = mutableSetOf<String>()

    fun registerNewPromise(req: Any): Future<*> {
        val futureList = mutableListOf<Future<*>>()
        for (id in requestId(req as REQ)) {
            val p = Promise.promise<RESP>()
            idPromiseMap[id] = p
            futureList.add(p.future())
        }
        return CompositeFuture.all(futureList)
    }

    fun getPromiseOfRequest(req: Any): Promise<RESP> {
        return idPromiseMap[requestId(req as REQ)[0]]!!
    }

    open fun preEncode(ctx: ChannelHandlerContext?, msg: REQ, out: MutableList<Any>?): Boolean = true

    open fun preDecode(ctx: ChannelHandlerContext?, msg: RESP, out: MutableList<Any>?): Boolean = true

    override fun encode(ctx: ChannelHandlerContext?, msg: REQ, out: MutableList<Any>?) {
        if (!preEncode(ctx, msg, out)) {
            return
        }
        ctx!!.channel().eventLoop().schedule({
            for (id in requestId(msg)) {
                if (!idPromiseMap[id]!!.future().isComplete) {
                    logger.error("订阅超时 $msg")
//                    idPromiseMap[id]!!.fail("订阅超时 $msg")
                    idPromiseMap[id]!!.complete()
                } else {
                    idPromiseMap.remove(id)
                }
            }
        }, 10, TimeUnit.SECONDS)
        out!!.add(msg as Any)
    }

    override fun decode(ctx: ChannelHandlerContext?, msg: RESP, out: MutableList<Any>?) {
        if (!preDecode(ctx, msg, out)) {
            return
        }
        logger.debug("从 ${ctx!!.channel().remoteAddress()} 订阅 $msg 成功")
        for (id in responseId(msg)) {
            idPromiseMap[id]!!.complete(msg)
        }
    }

    abstract fun isSubscriptionSuccess(msg: RESP): Boolean

    abstract fun requestId(req: REQ): List<String>

    abstract fun responseId(resp: RESP): List<String>
}

abstract class ExchangeSubscriptionBootstrap(
    private val subscriptionHandler: ChannelInboundHandler,
    private val channel: Channel
) {

    lateinit var promise: Promise<Any>

    fun subscribeAll() {
        channel.pipeline().addLast(subscriptionHandler)
        runBlocking {
            promise.complete()
        }
    }

    abstract fun buildDepthSubscription(): Any

    abstract fun buildBalanceSubscription(): Any

    abstract fun buildOrderDealSubscription(): Any

}