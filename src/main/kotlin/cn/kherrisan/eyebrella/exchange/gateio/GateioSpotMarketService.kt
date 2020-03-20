package cn.kherrisan.eyebrella.exchange.gateio

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotMarketService
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class GateioSpotMarketService @Autowired constructor(
        staticConfiguration: GateioStaticConfiguration,
        dataAdaptor: GateioServiceDataAdaptor,
        metaInfo: GateioMetaInfo,
        val runtimeConfig: GateioRuntimeConfig
) : AbstractSpotMarketService(staticConfiguration, dataAdaptor, metaInfo) {

    @Autowired
    override lateinit var dispatcher: GateioSpotMarketWebsocketDispatcher

    override suspend fun getSymbols(): List<Symbol> {
        val resp = get(publicUrl("/api2/1/pairs"))
        return jsonArray(resp).map { symbol(it.asString) }
    }

    override suspend fun getSymbolMetaInfo(): List<SymbolMetaInfo> {
        val resp = get(publicUrl("/api2/1/marketinfo"))
        return jsonObject(resp)["pairs"].asJsonArray.map { it.asJsonObject }
                .map {
                    it.entrySet().map { e ->
                        SymbolMetaInfo(symbol(e.key),
                                e.value.asJsonObject["min_amount"].asBigDecimal,
                                e.value.asJsonObject["amount_decimal_places"].asInt,
                                e.value.asJsonObject["decimal_places"].asInt,
                                e.value.asJsonObject["min_amount_b"].asBigDecimal.precision())
                    }.get(0)
                }.sortedBy { it.symbol.base.name }
    }

    override suspend fun getCurrencies(): List<Currency> {
        val symbols = getSymbols()
        return symbols.flatMap { listOf(it.base, it.quote) }
                .distinct().sortedBy { it.name }
    }

    override suspend fun getDepths(symbol: Symbol, size: Int): Depth {
        val resp = get(publicUrl("https://data.gateio.life/api2/1/orderBook/${string(symbol)}"))
        val obj = jsonObject(resp)
        val askMap = HashMap<BigDecimal, BigDecimal>()
        val bidMap = HashMap<BigDecimal, BigDecimal>()
        obj["asks"].asJsonArray.map { it.asJsonArray }
                .forEach { askMap[it[0].asBigDecimal] = it[1].asBigDecimal }
        obj["bids"].asJsonArray.map { it.asJsonArray }
                .forEach { bidMap[it[0].asBigDecimal] = it[1].asBigDecimal }
        return Depth(symbol, MyDate(), askMap, bidMap)
    }

    override suspend fun getKlines(symbol: Symbol, periodEnum: KlinePeriodEnum, size: Int, since: Date?): List<Kline> {
        val secUnit = periodEnum.toSeconds()
        val params = mutableMapOf("group_sec" to secUnit.toString(),
                "range_hour" to (size * secUnit / 3600).toString())
        val resp = get(publicUrl("https://data.gateio.life/api2/1/candlestick2/${string(symbol)}"), params)
        return jsonObject(resp)["data"].asJsonArray
                .map { it.asJsonArray }
                .map {
                    Kline(
                            symbol,
                            MyDate(it[0].asLong),
                            it[5].asBigDecimal,
                            it[2].asBigDecimal,
                            it[3].asBigDecimal,
                            it[4].asBigDecimal,
                            it[1].asBigDecimal
                    )
                }
    }

    /**
     * 订阅深度数据
     *
     * Notify market depth update information,使用clean字段来表示是全量数据还是增量数据
     * 每秒一次，很佛系
     *
     * @param symbol Symbol
     * @return Subscription<Trade>
     */
    override suspend fun subscribeDepth(symbol: Symbol): Subscription<Depth> {
        val ch = "depth:${Gson().toJson(listOf(string(symbol), 5, "0.01"))}"
        val dedicatedDispatcher = dispatcher.newChildDispatcher()
        return dedicatedDispatcher.newSubscription<Depth>(ch) { obj, sub ->
            val params = obj.asJsonObject["params"].asJsonArray
            val clean = params[0].asBoolean
            if (clean) {
                //全量
                val depth = depth(symbol, params[1].asJsonObject)
                sub.data = depth
            } else {
                //增量
                val inc = depth(symbol, params[1].asJsonObject)
                if (sub.data != null) {
                    val baseDepth = sub.data as Depth
                    baseDepth.merge(inc)
                    sub.deliver(baseDepth)
                }
            }
        }.subscribe()
    }

    override suspend fun subscribeTrade(symbol: Symbol): Subscription<Trade> {
        val args = "trades:${Gson().toJson(listOf(string(symbol)))}"
        return dispatcher.newSubscription<Trade>(args) { obj, sub ->
            val params = obj.asJsonObject["params"].asJsonArray[1].asJsonArray
            params.map { it.asJsonObject }
                    .forEach {
                        val trade = Trade(symbol,
                                it["id"].asInt.toString(),
                                MyDate((it["time"].asFloat * 1000).toLong()),
                                it["amount"].asBigDecimal,
                                it["price"].asBigDecimal,
                                orderSide(it["type"].asString)
                        )
                        sub.deliver(trade)
                    }
        }.subscribe()
    }

    override suspend fun subscribeKline(symbol: Symbol, period: KlinePeriodEnum): Subscription<Kline> {
        val args = "kline:${listOf(string(symbol), string(period).toInt())}"
        val dedicatedDispatcher = dispatcher.newChildDispatcher()
        return dedicatedDispatcher.newSubscription<Kline>(args) { obj, sub ->
            obj.asJsonObject["params"].asJsonArray.map { it.asJsonArray }
                    .map {
                        Kline(symbol,
                                MyDate(it[0].asLong * 1000),
                                it[1].asBigDecimal,
                                it[2].asBigDecimal,
                                it[3].asBigDecimal,
                                it[4].asBigDecimal,
                                it[5].asBigDecimal)
                    }
                    .forEach { sub.deliver(it) }
        }.subscribe()
    }
}