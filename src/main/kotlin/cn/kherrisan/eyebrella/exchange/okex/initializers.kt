package cn.kherrisan.eyebrella.exchange.okex

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

class OkexSpotMarketChannelInitializer(
    uri: String,
    val spot: AbstractSpot
) : AbstractChannelInitializer(uri) {

    override fun onHandshakeComplete(ctx: ChannelHandlerContext) {
        ctx.pipeline()
            .addLast(GzipDecompressor(true))
            .addLast(OkexPingPongHandler())
            .addLast(JsonCodec())
            .addLast(OkexMarketMessageDecoder())
            .addLast(OkexMarketSubscriptionHandler())
            .addLast(ExchangeBBOHandler(spot))
    }
}