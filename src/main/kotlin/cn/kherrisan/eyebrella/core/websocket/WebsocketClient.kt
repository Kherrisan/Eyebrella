package cn.kherrisan.eyebrella.core.websocket

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.SubscriptionHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import org.apache.logging.log4j.LogManager
import java.net.URI
import kotlin.reflect.KClass

typealias EventHandler<REQ> = (Channel) -> REQ

class WebsocketClient(
    private val initializer: AbstractChannelInitializer,
    private val spot: AbstractSpot
) {

    private val logger = LogManager.getLogger()
    private val handshakeEventHandlers = mutableListOf<EventHandler<Any>>()
    private val authenticationEventHandlers = mutableListOf<EventHandler<Any>>()
    var retrials = 0
    lateinit var connectedFuture: ChannelFuture
    lateinit var handshakeFuture: Future<Channel>
    lateinit var authenticationFuture: Future<Channel>

    init {
        initializer.client = this
        connectToRemote()
    }

    private fun triggerHandshakeEvent(channel: Channel) {
        handshakeEventHandlers.forEach {
            it(channel)
        }
    }

    private fun triggerAuthenticationEvnet(channel: Channel) {
        authenticationEventHandlers.forEach {
            it(channel)
        }
    }

    private fun triggerConnectionEvnet(channel: Channel) {
        retrials = 0
        connectedFuture.channel().pipeline().addFirst(AutoReconnectingHandler(this, spot))
        if (handshakeEventHandlers.isNotEmpty()) {
            handshakeFuture.onComplete {
                triggerHandshakeEvent(handshakeFuture.result())
            }
            authenticationFuture.onComplete {
                triggerAuthenticationEvnet(authenticationFuture.result())
            }
        }
    }

    fun connectToRemote(): ChannelFuture {
        initializer.handshakePromise = Promise.promise()
        initializer.authenticationPromise = Promise.promise()
        handshakeFuture = initializer.handshakePromise.future()
        authenticationFuture = initializer.authenticationPromise.future()
        val uri = URI(initializer.uri)
        var port: Int = uri.port
        if (port == -1) {
            port = if (uri.scheme == "wss") {
                443
            } else {
                80
            }
        }
        val bootstrap = Bootstrap()
            .group(NioEventLoopGroup())
            .channel(NioSocketChannel::class.java)
            .handler(initializer)
        logger.debug("开始连接到 $uri")
        connectedFuture = bootstrap.connect(uri.host, port)
        connectedFuture.addListener {
            if (it.isSuccess) {
                logger.debug("已连接到 $uri")
                triggerConnectionEvnet(connectedFuture.channel())
            } else {
                logger.error("无法连接到 $uri")
                logger.error("第 $retrials 次连接失败，30s 后重新连接")
                if (retrials >= 10) {
                    logger.error("重新连接超过10次")
                    return@addListener
                }
                Thread.sleep(30_000)
                connectToRemote()
            }
        }
        return connectedFuture
    }

    suspend fun <T : ChannelHandler> get(handlerClass: KClass<T>): T {
        handshakeFuture.await()
        return connectedFuture.channel().pipeline().get(handlerClass.java)
    }

    fun write(msg: Any) {
        handshakeFuture.onComplete {
            it.result().writeAndFlush(msg)
        }
    }

    fun shutdown() {
        connectedFuture.channel().eventLoop().shutdownGracefully()
    }

    suspend fun onHandshaked(handler: EventHandler<Any>): Future<*> {
        handshakeFuture.await()
        handshakeEventHandlers.add(handler)
        val req = handler(connectedFuture.channel())
        return connectedFuture.channel()
            .pipeline()
            .get(SubscriptionHandler::class.java)
            .registerNewPromise(req)
    }

    suspend fun onAuthenticated(handler: EventHandler<Any>): Future<*> {
        authenticationFuture.await()
        authenticationEventHandlers.add(handler)
        val req = handler(connectedFuture.channel())
        return connectedFuture.channel()
            .pipeline()
            .get(SubscriptionHandler::class.java)
            .registerNewPromise(req)
    }
}