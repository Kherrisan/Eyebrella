package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Symbol
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class OkexSpot @Autowired constructor(
    market: OkexSpotMarketService,
    trading: OkexSpotTradingService,
    rt: OkexRuntimeConfig,
    val sc: OkexStaticConfiguration
) : AbstractSpot(market, trading, rt, ExchangeName.OKEX) {

    private val marketWs = WebsocketClient(OkexSpotMarketChannelInitializer(sc.spotMarketWsHost, this), this)

    override suspend fun CoroutineScope.subscribeAll() {
        launch {
            subscribeBBO()
        }
    }

    override suspend fun subscribeBBO(symbol: Symbol?) {
        val symbolArgs = symbols()
            .map { "spot/ticker:${it.base.toUpperCase()}-${it.quote.toUpperCase()}" }
        val req = OkexMarketSubscriptionRequest("subscribe", symbolArgs)
        marketWs.onHandshaked { channel ->
            channel.writeAndFlush(req)
            req
        }.await()
    }

    override suspend fun subscribeDepth(symbol: Symbol?) {
        throw NotImplementedError()
    }

    override suspend fun subscribeKline(symbol: Symbol?) {
        throw NotImplementedError()
    }

    override suspend fun subscribeBalance(currency: Currency?) {
        TODO("Not yet implemented")
    }

    override suspend fun subscribeSpotOrderDeal(symbol: Symbol?) {
        TODO("Not yet implemented")
    }

    override suspend fun updateHalfRTT() {
        return
    }
}