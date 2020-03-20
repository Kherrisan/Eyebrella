package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotMarketService
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class BinanceSpotMarketService @Autowired constructor(
        staticConfiguration: BinanceStaticConfiguration,
        dataAdaptor: BinanceServiceDataAdaptor,
        metaInfo: BinanceMetaInfo,
        val runtimeConfig: BinanceRuntimeConfig
) : AbstractSpotMarketService(staticConfiguration, dataAdaptor, metaInfo) {

    @Autowired
    private lateinit var vertx: Vertx

    @Autowired
    override lateinit var dispatcher: BinanceSpotMarketWebsocketDispatcher

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val obj = JsonParser.parseString(resp.bodyAsString())
        if (resp.statusCode() != 200) {
            logger.error(obj)
            error(obj)
        }
        return obj
    }

    override suspend fun getSymbols(): List<Symbol> {
        val resp = get(publicUrl("/api/v3/exchangeInfo"))
        val obj = jsonObject(resp)
        return obj["symbols"].asJsonArray.map { it.asJsonObject }
                .map {
                    Symbol(it["quoteAsset"].asString.toLowerCase(),
                            it["baseAsset"].asString.toLowerCase()
                    )
                }
                .sortedBy { it.base.name }
    }

    override suspend fun getSymbolMetaInfo(): List<SymbolMetaInfo> {
        //交易规范信息
        val resp = get(publicUrl("/api/v3/exchangeInfo"))
        val obj = jsonObject(resp)
        return obj["symbols"].asJsonArray.map { it.asJsonObject }
                .map {
                    SymbolMetaInfo(
                            Symbol(it["baseAsset"].asString.toLowerCase(),
                                    it["quoteAsset"].asString.toLowerCase()
                            ),
                            0f.toBigDecimal(),
                            it["baseAssetPrecision"].asInt,
                            it["quotePrecision"].asInt,
                            it["quotePrecision"].asInt
                    )
                }.sortedBy { it.symbol.base.name }
    }

    override suspend fun getCurrencies(): List<Currency> {
        return getSymbols().flatMap { listOf(it.base, it.quote) }
                .distinct()
                .sortedBy { it.name }
    }

    override suspend fun getDepths(symbol: Symbol, size: Int): Depth {
        logger.debug("Start to request depths: ${string(symbol)}")
        val resp = get(publicUrl("/api/v3/depth"), mutableMapOf(
                "symbol" to string(symbol),
                "limit" to size.toString()
        ))
        val obj = jsonObject(resp)
        return SequentialDepth(depth(symbol, obj), obj["lastUpdateId"].asLong, 0L)
    }

    /**
     * 获得K线数据
     *
     * @param symbol Symbol
     * @param periodEnum KlinePeriodEnum
     * @param size Int 最大1000
     * @param since Date?
     * @return List<Kline>
     */
    override suspend fun getKlines(symbol: Symbol, periodEnum: KlinePeriodEnum, size: Int, since: Date?): List<Kline> {
        val params = mutableMapOf(
                "symbol" to symbol.nameWithoutSlash().toUpperCase(),
                "interval" to string(periodEnum),
                "limit" to size.toString())
        since?.let { params["startTime"] = string(since) }
        val resp = get(publicUrl("/api/v3/klines"), params)
        val arr = jsonArray(resp)
        return arr.map { it.asJsonArray }
                .map {
                    Kline(
                            symbol,
                            date(it[0].asLong.toString()),
                            price(it[1], symbol),
                            price(it[4], symbol),
                            price(it[2], symbol),
                            price(it[3], symbol),
                            volume(it[5], symbol)
                    )
                }
                .sortedBy { it.time }
    }

    /**
     * 订阅深度信息
     *
     * 1000档数据，增量更新，每100ms更新一次。
     *
     * @param symbol Symbol
     * @return Subscription<Depth>
     */
    override suspend fun subscribeDepth(symbol: Symbol): Subscription<Depth> {
        var baseDepthPromise = Promise.promise<SequentialDepth>()
        val ch = "${symbol.nameWithoutSlash()}@depth@100ms"
        val dedicatedDispatcher = dispatcher.newChildDispatcher()
        val sub = dedicatedDispatcher.newSubscription<Depth>(ch) { it, sub ->
            try {
                val obj = it.asJsonObject
                val askMap = ConcurrentHashMap<BigDecimal, BigDecimal>()
                val bidMap = ConcurrentHashMap<BigDecimal, BigDecimal>()
                obj["a"].asJsonArray.map { it.asJsonArray }.forEach {
                    askMap[price(it[0], symbol)] = size(it[1], symbol)
                }
                obj["b"].asJsonArray.map { it.asJsonArray }.forEach {
                    bidMap[price(it[0], symbol)] = size(it[1], symbol)
                }
                //Binance的增量数据代表了一个事件区间内的ask和bid的变化量，并且给出了该事件区间开始的序号和结束的序号
                //各个增量的序号不会重叠，相连的增量的序号差为1
                //这里使用开始的序号作为prev，结束的序号作为seq
                val inc = SequentialDepth(symbol, MyDate(), askMap, bidMap, obj["u"].asLong, obj["U"].asLong)
                sub.buffer.add(inc)
                if (baseDepthPromise.future().isComplete && sub.data == null) {
                    sub.data = baseDepthPromise.future().result()
                }
                if (sub.data != null) {
                    //更新增量数据
                    val baseDepth = sub.data as SequentialDepth
                    val oldDepth = sub.buffer.map { it as SequentialDepth }.filter { it.seq <= baseDepth.seq }
                    //把老旧的增量数据删除
                    sub.buffer.removeAll(oldDepth)
                    for (i in sub.buffer.map { it as SequentialDepth }.sortedBy { it.prev }) {
                        if (i.prev > baseDepth.seq + 1) {
                            break
                        } else if (baseDepth.prev == 0L) {
                            //落在第一个增量数据的区间内[prev,seq]，且全量数据没有更新过
                            baseDepth.merge(i)
                            baseDepth.prev = i.prev
                            baseDepth.seq = i.seq
                        } else if (baseDepth.seq + 1 == i.prev) {
                            baseDepth.merge(i)
                            baseDepth.seq = i.seq
                        } else {
                            //增量数据不连续
                            launch(vertx.dispatcher()) {
                                baseDepthPromise = Promise.promise()
                                val newBaseDepth = getDepths(symbol, 1000) as SequentialDepth
                                baseDepthPromise.complete(newBaseDepth)
                            }
                        }
                    }
                    sub.deliver(baseDepth)
                }
            } catch (e: Exception) {
                logger.error(e)
                logger.error(it)
            }
        }.subscribe()
        delay(1000)
        val baseDepth = getDepths(symbol, 1000) as SequentialDepth
        baseDepthPromise.complete(baseDepth)
        return sub
    }

    override suspend fun subscribeTrade(symbol: Symbol): Subscription<Trade> {
        val ch = "${symbol.nameWithoutSlash()}@trade"
        return dispatcher.newSubscription<Trade>(ch) { it, sub ->
            val obj = it.asJsonObject
            sub.deliver(Trade(symbol,
                    obj["t"].asString,
                    date(obj["T"].asLong.toString()),
                    size(obj["q"], symbol),
                    price(obj["p"], symbol),
                    orderSide(obj["m"].asBoolean.toString())
            ))
        }.subscribe()
    }

    override suspend fun subscribeKline(symbol: Symbol, period: KlinePeriodEnum): Subscription<Kline> {
        val ch = "${symbol.nameWithoutSlash()}@kline_${string(period)}"
        return dispatcher.newSubscription<Kline>(ch) { it, sub ->
            val k = it.asJsonObject["k"].asJsonObject
            sub.deliver(Kline(symbol,
                    date(it.asJsonObject["E"].asLong.toString()),
                    price(k["o"], symbol),
                    price(k["c"], symbol),
                    price(k["h"], symbol),
                    price(k["l"], symbol),
                    size(k["v"], symbol)))
        }.subscribe()
    }
}