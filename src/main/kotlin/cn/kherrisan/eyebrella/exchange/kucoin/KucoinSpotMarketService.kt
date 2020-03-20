package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.common.POST
import cn.kherrisan.eyebrella.core.common.md5
import cn.kherrisan.eyebrella.core.common.uuid
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.http.HttpMediaTypeEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotMarketService
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*
import javax.annotation.PostConstruct

@Component
class KucoinSpotMarketService @Autowired constructor(
        staticConfiguration: KucoinStaticConfiguration,
        dataAdaptor: KucoinSerivceDataAdaptor,
        metaInfo: KucoinMetaInfo,
        val runtimeConfig: KucoinRuntimeConfig
) : AbstractSpotMarketService(staticConfiguration, dataAdaptor, metaInfo) {

    private val auth = KucoinAuthenticateService(staticConfiguration.spotMarketHttpHost)

    @PostConstruct
    fun initInstanceServer() {
        runBlocking {
            val instance = getInstanceServer()
            staticConfig.spotMarketWsHost = "${instance.url}?token=${instance.token}&connectId=${md5(uuid())}&acceptUserMessage=true"
            runtimeConfig.pingTimeout = instance.pingTimeout
            runtimeConfig.pingInterval = instance.pingInterval
            val privateInstance = getPrivateInstanceServer()
            staticConfig.spotTradingWsHost = "${privateInstance.url}?token=${privateInstance.token}&connectId=${md5(uuid())}&acceptUserMessage=true"
        }
    }

    suspend fun signedPost(url: String, params: MutableMap<String, String> = mutableMapOf(), headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer> {
        @Suppress("UNCHECKED_CAST")
        auth.signedHttpRequest(POST, url, params as MutableMap<String, Any>, headers)
        return post(url, HttpMediaTypeEnum.JSON, params, headers)
    }

    suspend fun getPrivateInstanceServer(): KucoinInstanceServer {
        val resp = signedPost(publicUrl("/api/v1/bullet-private"))
        val data = jsonObject(resp)["data"].asJsonObject
        val instant = data["instanceServers"].asJsonArray[0].asJsonObject
        return KucoinInstanceServer(instant["endpoint"].asString,
                instant["pingInterval"].asInt,
                instant["pingTimeout"].asInt,
                data["token"].asString)
    }

    suspend fun getInstanceServer(): KucoinInstanceServer {
        val resp = post(publicUrl("/api/v1/bullet-public"))
        val data = jsonObject(resp)["data"].asJsonObject
        val instant = data["instanceServers"].asJsonArray[0].asJsonObject
        return KucoinInstanceServer(instant["endpoint"].asString,
                instant["pingInterval"].asInt,
                instant["pingTimeout"].asInt,
                data["token"].asString)
    }

    override suspend fun getSymbols(): List<Symbol> {
        val resp = get(publicUrl("/api/v1/symbols"))
        return jsonObject(resp)["data"].asJsonArray.map { it.asJsonObject }
                .map { Symbol(it["baseCurrency"].asString, it["quoteCurrency"].asString) }
    }

    override suspend fun getSymbolMetaInfo(): List<SymbolMetaInfo> {
        val resp = get(publicUrl("/api/v1/symbols"))
        return jsonObject(resp)["data"].asJsonArray.map { it.asJsonObject }
                .map {
                    SymbolMetaInfo(
                            Symbol(it["baseCurrency"].asString,
                                    it["quoteCurrency"].asString),
                            bigDecimal(it["baseMinSize"]),
                            bigDecimal(it["baseIncrement"]).precision(),
                            bigDecimal(it["priceIncrement"]).precision(),
                            bigDecimal(it["quoteIncrement"]).precision())
                }.sortedBy { it.symbol.base.name }
    }

    override suspend fun getCurrencies(): List<Currency> {
        val resp = get(publicUrl("/api/v1/currencies"))
        return jsonObject(resp)["data"].asJsonArray.map { it.asJsonObject }
                .map { Currency(it["currency"].asString.toLowerCase()) }
                .sortedBy { it.name }
    }

    /**
     * 获得深度信息
     *
     * @param symbol Symbol
     * @param size Int 只支持20和100
     * @return Depth
     */
    override suspend fun getDepths(symbol: Symbol, size: Int): Depth {
        if (size != 20 && size != 100) {
            error("Unsupported depth size: $size, depth size for Kucoin spot market is optional for 20 or 100.")
        }
        val resp = get(publicUrl("/api/v1/market/orderbook/level2_$size"),
                mutableMapOf("symbol" to string(symbol)))
        val data = jsonObject(resp)["data"].asJsonObject
        val depth = depth(symbol, data)
        depth.time = date(data["time"])
        return SequentialDepth(depth, data["sequence"].asString.toLong(), 0L)
    }

    override suspend fun getKlines(symbol: Symbol, periodEnum: KlinePeriodEnum, size: Int, since: Date?): List<Kline> {
        val params = mutableMapOf("symbol" to string(symbol),
                "type" to string(periodEnum))
        if (since != null) {
            params["startAt"] = (since.time / 1000).toString()
            params["endAt"] = (periodEnum.toSeconds() * size + since.time / 1000).toString()
        }
        val resp = get(publicUrl("/api/v1/market/candles"), params)
        return jsonObject(resp)["data"].asJsonArray.map { it.asJsonArray }
                .map {
                    Kline(symbol,
                            MyDate(it[0].asString.toLong() * 1000),
                            bigDecimal(it[1]),
                            bigDecimal(it[2]),
                            bigDecimal(it[3]),
                            bigDecimal(it[4]),
                            bigDecimal(it[5]))
                }
                .sortedBy { it.time }
    }

    /**
     * 订阅深度数据
     *
     * 订阅增量数据，并整合到rest接口获得的全量深度上，默认使用100档的深度。
     * Level-2 Market Data
     *
     * @param symbol Symbol
     * @return Subscription<Depth>
     */
    override suspend fun subscribeDepth(symbol: Symbol): Subscription<Depth> {
        val ch = "/market/level2:${string(symbol)}"
        val sub = dispatcher.newSubscription<Depth>(ch) { it, sub ->
            val data = it.asJsonObject["data"].asJsonObject
            val askChanges = data["changes"].asJsonObject["asks"].asJsonArray
                    .map { it.asJsonArray }
                    .map {
                        DepthChange(bigDecimal(it[0]),
                                bigDecimal(it[1]),
                                it[2].asString.toLong(),
                                OrderSideEnum.SELL)
                    }
            val bidChanges = data["changes"].asJsonObject["bids"].asJsonArray
                    .map { it.asJsonArray }
                    .map {
                        DepthChange(bigDecimal(it[0]),
                                bigDecimal(it[1]),
                                it[2].asString.toLong(),
                                OrderSideEnum.BUY)
                    }
            sub.buffer.addAll(askChanges)
            sub.buffer.addAll(bidChanges)
            if (sub.data != null) {
                val baseDepth = sub.data as SequentialDepth
                val oldChanges = sub.buffer.map { it as DepthChange }.filter { it.seq < baseDepth.seq }
                sub.buffer.removeAll(oldChanges)
                val changes = sub.buffer.map { it as DepthChange }.sortedBy { it.seq }
                for (change in changes) {
                    if (change.seq == baseDepth.seq || change.seq == baseDepth.seq + 1) {
                        //检查单调递增性
                        if (change.size.compareTo(BigDecimal.ZERO) == 0) {
                            if (change.side == OrderSideEnum.BUY) {
                                baseDepth.bids.removeIf { it.price.compareTo(change.price) == 0 }
                            } else {
                                baseDepth.asks.removeIf { it.price.compareTo(change.price) == 0 }
                            }
                        } else {
                            if (change.side == OrderSideEnum.BUY) {
                                val t = baseDepth.bids.find { it.price.compareTo(change.price) == 0 }
                                if (t != null) {
                                    t.amount = change.size
                                } else {
                                    baseDepth.bids.add(DepthItem(change.price, change.size))
                                    baseDepth.bids.sortDescending()
                                }
                            } else {
                                val t = baseDepth.asks.find { it.price.compareTo(change.price) == 0 }
                                if (t != null) {
                                    t.amount = change.size
                                } else {
                                    baseDepth.asks.add(DepthItem(change.price, change.size))
                                    baseDepth.asks.sortDescending()
                                }
                            }
                        }
                        baseDepth.seq = change.seq
                    } else {
                        break
                    }
                }
                sub.deliver(sub.data as SequentialDepth)
            }
        }.subscribe()
        delay(1000)
        val baseDepth = getDepths(symbol, 20) as SequentialDepth
        sub.data = baseDepth
        return sub
    }

    /**
     * 订阅交易数据
     *
     * 需要使用privatechannel，另外开一个dispatcher
     *
     * @param symbol Symbol
     * @return Subscription<Trade>
     */
    override suspend fun subscribeTrade(symbol: Symbol): Subscription<Trade> {
        val dedicatedDispatcher = dispatcher.newChildDispatcher()
        val ch = "/market/match:${string(symbol)}"
        val sub = dedicatedDispatcher.newSubscription<Trade>(ch) { it, sub ->
            val data = it.asJsonObject["data"].asJsonObject
            sub.deliver(Trade(symbol,
                    data["tradeId"].asString,
                    MyDate(data["time"].asString.toLong() / 1000000),
                    bigDecimal(data["size"]),
                    bigDecimal(data["price"]),
                    orderSide(data["side"])))
        }
        return sub.subscribe()
    }

    /**
     * 订阅K线数据
     *
     * Kucoin不支持订阅K线数据
     *
     * @param symbol Symbol
     * @param period KlinePeriodEnum
     * @return Subscription<Kline>
     */
    override suspend fun subscribeKline(symbol: Symbol, period: KlinePeriodEnum): Subscription<Kline> {
        throw NotImplementedError()
    }
}