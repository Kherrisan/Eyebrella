package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.websocket.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import java.net.URI
import javax.net.ssl.SSLContext

abstract class HuobiChannelInitializer(uri: String, val spot: AbstractSpot) :
    AbstractChannelInitializer(uri) {

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

class HuobiSpotMarketChannelInitializer(
    uri: String,
    spot: AbstractSpot
) : HuobiChannelInitializer(uri, spot) {

    override fun onHandshakeComplete(ctx: ChannelHandlerContext) {
        ctx.pipeline()
            .addLast(GzipDecompressor())
            .addLast(JsonCodec())
            .addLast(HuobiMarketMessageDecoder())
            .addLast(HuobiMarketSubscriptionHandler())
            .addLast(HuobiMarketPingPongHandler())
            .addLast(HuobiSpotDepthHandler(spot))
            .addLast(HuobiSpotBBOHandler(spot))
            .addLast(HuobiSpotKlineHandler(spot))
    }
}

class HuobiSpotTradingChannelInitializer(
    uri: String,
    spot: AbstractSpot,
    private val sharedBalanceHandler: HuobiSpotBalanceHandler
) : HuobiChannelInitializer(uri, spot) {

    override fun onHandshakeComplete(ctx: ChannelHandlerContext) {
        ctx.pipeline()
            .addLast(GzipDecompressor())
            .addLast(JsonCodec())
            .addLast(HuobiTradingMessageDecoder())
            .addLast(HuobiTradingSubscriptionHandler())
            .addLast(HuobiAuthenticationHandler(authenticationPromise))
            .addLast(HuobiTradingPingPongHander())
            .addLast(HuobiSpotOrderDealHandler(spot))
            .addLast(sharedBalanceHandler)
    }
}