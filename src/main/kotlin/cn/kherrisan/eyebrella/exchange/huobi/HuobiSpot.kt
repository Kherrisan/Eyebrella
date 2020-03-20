package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import io.vertx.kotlin.coroutines.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

@Component
class HuobiSpot @Autowired constructor(
    market: HuobiSpotMarketService,
    trading: HuobiSpotTradingService,
    rt: HuobiRuntimeConfig,
    val sc: HuobiStaticConfiguration
) : AbstractSpot(market, trading, rt, ExchangeName.HUOBI) {

    private val symbolsSupportedMBP = rt.symbols!!
    private val balanceHandler = HuobiSpotBalanceHandler(this)
    private val marketWs = buildWebsocketClient(HuobiSpotMarketChannelInitializer(sc.spotMarketWsHost, this))
    private val tradingWs =
        buildWebsocketClient(HuobiSpotTradingChannelInitializer(sc.spotTradingWsHost, this, balanceHandler))

    fun requestBaseDepth(symbol: Symbol) {
        val ch = "market.${symbol.nameWithoutSlash()}.mbp.150"
        marketWs.write(HuobiMarketRequest(ch))
    }

    override suspend fun subscribeBBO(symbol: Symbol?) {
        val ch = "market.${symbol!!.nameWithoutSlash()}.bbo"
        val req = HuobiMarketSubscriptionRequest(ch)
        marketWs.onHandshaked { channel ->
            channel.writeAndFlush(req)
            req
        }.await()
    }

    override suspend fun subscribeDepth(symbol: Symbol?) {
        if (symbol in symbolsSupportedMBP) {
            val ch = "market.${symbol!!.nameWithoutSlash()}.mbp.150"
            val req = HuobiMarketSubscriptionRequest(ch)
            marketWs.onHandshaked { channel ->
                requestBaseDepth(symbol)
                channel.writeAndFlush(req)
                req
            }.await()
        } else {
//            val ch = "market.${symbol.nameWithoutSlash()}.depth.step0"
//            val req = HuobiMarketSubscriptionRequest(ch)
//            marketWs.addHandshakeListenerFactory {
//                HuobiMarketSubscriptionHandler(req)
//            }.await()
        }
//        worker.launchPeriodicalJob(10_000) {
//            try {
//                logger.debug("深度数据入库")
//                val snapshot = depth(symbol).copy()
//                depthRepository.save(ExchangeName.HUOBI, snapshot)
//            } catch (e: Exception) {
//                logger.error(e)
//                e.printStackTrace()
//            }
//        }
    }

    override suspend fun subscribeKline(symbol: Symbol?) {
        val ch = "market.${symbol!!.nameWithoutSlash()}.kline.1day"
        val req = HuobiMarketSubscriptionRequest(ch)
        marketWs.onHandshaked { channel ->
            channel.writeAndFlush(req)
            req
        }.await()
    }

    override suspend fun subscribeBalance(currency: Currency?) {
        tradingWs.authenticationFuture.onComplete {
            it.result().attr(SPOT_BALANCE_MODEL_ATTR).set("0")
        }
        val req = HuobiTradingSubscriptionRequest("sub", "accounts")
        val future = tradingWs.onAuthenticated { channel ->
            channel.writeAndFlush(req)
            req
        }
        sendSubscriptionMessage(req, tradingWs)
        val includeFrozenWs =
            buildWebsocketClient(HuobiSpotTradingChannelInitializer(sc.spotTradingWsHost, this, balanceHandler))
        val infReq = HuobiTradingSubscriptionRequest("sub", "accounts", "1")
        future.await()
        includeFrozenWs.onAuthenticated { channel ->
            channel.writeAndFlush(infReq)
            infReq
        }.await()
    }

    override suspend fun subscribeSpotOrderDeal(symbol: Symbol?) {
        val req = HuobiTradingSubscriptionRequest("sub", "orders.*.update")
        tradingWs.onAuthenticated {
            channel -> channel.writeAndFlush(req)
            req
        }.await()
//        val req = HuobiTradingSubscriptionRequest("sub", "orders.*.update")
//        sendSubscriptionMessage(req, tradingWs)
    }

    override suspend fun updateHalfRTT() {
        val ts = Date()
        val remoteTs = (market as HuobiSpotMarketService).getTimestamp()
        halfRTT = remoteTs - ts.time
        logger.debug("与 $name 的单程网络时延为 ${halfRTT}ms")
    }
}