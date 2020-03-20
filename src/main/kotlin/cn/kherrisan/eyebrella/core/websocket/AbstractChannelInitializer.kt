package cn.kherrisan.eyebrella.core.websocket

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import io.vertx.core.Promise
import java.net.URI
import javax.net.ssl.SSLContext

abstract class AbstractChannelInitializer(val uri: String) : ChannelInitializer<Channel>() {

    lateinit var client: WebsocketClient
    lateinit var handshakePromise: Promise<Channel>
    lateinit var authenticationPromise: Promise<Channel>

    abstract fun onHandshakeComplete(ctx: ChannelHandlerContext)

    private fun sslHandler(): SslHandler {
        val sslEngine = SSLContext.getDefault().createSSLEngine()
        sslEngine.useClientMode = true
        return SslHandler(sslEngine)
    }

    override fun initChannel(ch: Channel?) {
        ch!!.pipeline()
            .addLast(LoggingHandler(LogLevel.TRACE))
            .addLast(sslHandler())
            .addLast(HttpClientCodec())
            .addLast(HttpObjectAggregator(65536))
            .addLast(
                WebSocketClientProtocolHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                        URI(uri),
                        WebSocketVersion.V13,
                        null,
                        false,
                        DefaultHttpHeaders()
                    )
                )
            )
            .addLast(WebsocketHandshakeListener(this))
    }

}