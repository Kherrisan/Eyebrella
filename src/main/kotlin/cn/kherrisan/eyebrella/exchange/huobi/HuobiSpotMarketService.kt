package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.randomDelay
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotMarketService
import cn.kherrisan.eyebrella.core.websocket.DefaultSubscription
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

@Component
class HuobiSpotMarketService @Autowired constructor(
    staticConfig: HuobiStaticConfiguration,
    dataAdaptor: HuobiServiceDataAdaptor,
    metaInfo: HuobiMetaInfo
) : AbstractSpotMarketService(staticConfig, dataAdaptor, metaInfo) {

    @Autowired
    @Qualifier("huobiSpotMarketWebsocketDispatcher")
    override lateinit var dispatcher: HuobiSpotMarketWebsocketDispatcher

    @Autowired
    private lateinit var vertx: Vertx

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val obj = JsonParser.parseString(resp.bodyAsString()).asJsonObject
        if (obj.has("status") && obj["status"].asString == "error") {
            logger.error(obj)
            error(obj)
        }
        return obj
    }

    override suspend fun getSymbols(): List<Symbol> {
        val resp = get(publicUrl("/v1/common/symbols"))
        val obj = jsonObject(resp)
        return obj["data"].asJsonArray
            .map { it.asJsonObject }
            .map {
                Symbol(
                    it["base-currency"].asString,
                    it["quote-currency"].asString
                )
            }
            .sortedBy { it.base.name }
    }

    override suspend fun getSymbolMetaInfo(): List<SymbolMetaInfo> {
        val resp = get(publicUrl("/v1/common/symbols"))
        return jsonObject(resp)["data"].asJsonArray
            .map { it.asJsonObject }
            .map {
                SymbolMetaInfo(
                    Symbol(
                        it["base-currency"].asString,
                        it["quote-currency"].asString
                    ),
                    it["min-order-amt"].asBigDecimal,
                    it["amount-precision"].asInt,
                    it["price-precision"].asInt,
                    it["value-precision"].asInt
                )
            }.sortedBy { it.symbol.base.name }
    }

    override suspend fun getCurrencies(): List<Currency> {
        val resp = get(publicUrl("/v1/common/currencys"))
        val obj = jsonObject(resp)
        return obj["data"].asJsonArray
            .map { it.asString.toLowerCase() }
            .sorted()
            .map { Currency(it) }
    }

    /**
     * 获得深度信息
     *
     * 支持按报价精度进行聚合，如step0代表无聚合，step5代表报价精度*100000
     *
     * @param symbol Symbol
     * @param size Int 5,20,100
     * @return Depth
     */
    override suspend fun getDepths(symbol: Symbol, size: Int): Depth {
        val resp = get(
            publicUrl("/market/depth"), mapOf(
                "symbol" to string(symbol),
                "type" to "step0",
                "depth" to size.toString()
            )
        )
        val obj = jsonObject(resp)
        val depth = depth(symbol, obj["tick"].asJsonObject)
        depth.time = date(obj["ts"].asLong.toString())
        return depth
    }

    /**
     * 获得K线数据
     *
     * Huobi不支持通过HTTP获得任意区间的K线
     *
     * @param symbol Symbol
     * @param periodEnum KlinePeriodEnum
     * @param size Int 取值范围最大2000，因为不支持任意范围的数据，所以当数量超过2000时也无法做到分多个请求然后join数据
     * @param since Date? 无效
     * @return List<Kline>
     */
    override suspend fun getKlines(symbol: Symbol, periodEnum: KlinePeriodEnum, size: Int, since: Date?): List<Kline> {
        if (size > 2000) {
            error("Maximum size of getKlines(huobi) is 2000")
        }
        val resp = get(
            publicUrl("/market/history/kline"), mapOf(
                "symbol" to string(symbol),
                "size" to size.toString(),
                "period" to string(periodEnum)
            )
        )
        val obj = jsonObject(resp)
        return obj["data"].asJsonArray
            .map { it.asJsonObject }
            .mapIndexed { _, it ->
                Kline(
                    symbol,
                    date((it["id"].asLong * 1000).toString()), // id为新加坡时间的时间戳
                    price(it["open"], symbol),
                    price(it["close"], symbol),
                    price(it["high"], symbol),
                    price(it["low"], symbol),
                    volume(it["vol"], symbol)
                )
            }
            .sortedBy { it.time }
    }

    private suspend fun doSubscribeDepth(sub: DefaultSubscription<Depth>, symbol: Symbol) {
        var hasRequestAgain = false
        var disorderCounter = 0
        //先订阅增量数据
        sub.resolver = { it, subscription ->
            val obj = it.asJsonObject
            if (obj.has("rep")) {
                //是针对req的响应报文，处理全量数据
                logger.debug("Get response from ${subscription.channel}")
                var depth = depth(symbol, it.asJsonObject["data"].asJsonObject)
                depth = SequentialDepth(depth, 0L, 0L)
                depth.seq = it.asJsonObject["data"].asJsonObject["seqNum"].asLong
                logger.debug("Base depth: $depth")
                subscription.data = depth
            } else {
                //是订阅报文，处理增量数据
                var depth = depth(symbol, it.asJsonObject["tick"].asJsonObject)
                depth = SequentialDepth(depth, 0L, 0L)
                depth.seq = it.asJsonObject["tick"].asJsonObject["seqNum"].asLong
                depth.prev = it.asJsonObject["tick"].asJsonObject["prevSeqNum"].asLong
                //缓存起来
                subscription.buffer.add(depth)
                if (subscription.data != null) {
                    var updated = false
                    val baseDepth = subscription.data as SequentialDepth
                    val afterInitDepth =
                        subscription.buffer.map { it as SequentialDepth }.filter { it.prev >= baseDepth.seq }
                            .sortedBy { it.seq }
                    for (inc in afterInitDepth) {
                        if (inc.prev == baseDepth.seq) {
                            baseDepth.merge(inc)
                            baseDepth.seq = inc.seq
                            updated = true
                            subscription.buffer.remove(inc)
                        } else {
                            //增量深度数据出现缺失，但也有可能只是暂时的失序，可以等一段时间
                            //但要记录等待的次数，避免一直处于失序-等待的状态中
                            logger.debug("Disorder appears, base seq is ${baseDepth.seq}, smallest prevSeq not smaller than base is ${inc.seq}")
                            disorderCounter = disorderCounter.inc()
                            if (disorderCounter >= 10) {
                                if (!hasRequestAgain) {
                                    logger.error("Uncontinutial increment data")
                                    //再请求一次全量数据，在此之前，把buffer中所有的prev大于base.seq的都删掉
                                    val before = subscription.buffer.map { it as SequentialDepth }
                                        .filter { it.prev < baseDepth.seq }
                                    subscription.buffer.remove(before)
                                    launch(vertx.dispatcher()) {
                                        subscription.data = null
                                        hasRequestAgain = true
                                        disorderCounter = 0
                                        hasRequestAgain = false
                                        // 随机等待一个时间
                                        randomDelay()
                                        subscription.request()
                                    }
                                }
                            }
                            break
                        }
                    }
                    if (updated) {
                        subscription.deliver(subscription.data as SequentialDepth)
                    }
                }
            }
        }
        sub.subscribe()
        delay(1000)
        sub.request()
    }

    /**
     * 订阅深度数据
     *
     * 建议下游数据处理方式：
    1） 订阅增量数据并开始缓存；
    2） 请求全量数据（同等档位数）并根据该全量消息的seqNum与缓存增量数据中的prevSeqNum对齐；
    3） 开始连续增量数据接收与计算，构建并持续更新MBP订单簿；
    4） 每条增量数据的prevSeqNum须与前一条增量数据的seqNum一致，否则意味着存在增量数据丢失，须重新获取全量数据并对齐；
    5） 如果收到增量数据包含新增price档位，须将该price档位插入MBP订单簿中适当位置；
    6） 如果收到增量数据包含已有price档位，但size不同，须替换MBP订单簿中该price档位的size；
    7） 如果收到增量数据某price档位的size为0值，须将该price档位从MBP订单簿中删除；
    8） 如果收到单条增量数据中包含两个及以上price档位的更新，这些price档位须在MBP订单簿中被同时更新。

    当前仅支持100ms快照MBP行情的增量推送，暂不支持更低快照间隔甚至逐笔MBP行情的增量推送。

     * 火币的MBP数据不是很稳定，具体体现在返回的增量数据的prevSeq进场和全量数据的seq对不上号，这时就只能在继续缓存增量数据的同时再次请求全量数据，
     * 以希望其能够seq和prevSeq能够对齐。
     *
     * @param symbol Symbol
     * @return Subscription<Depth>
     */
    override suspend fun subscribeDepth(symbol: Symbol): Subscription<Depth> {
        throw NotImplementedError()
    }

    /**
     * 订阅成交数据
     *
     * @param symbol Symbol
     * @return Subscription<Trade>
     */
    override suspend fun subscribeTrade(symbol: Symbol): Subscription<Trade> {
        val channel = "market.${symbol.nameWithoutSlash()}.trade.detail"
        return dispatcher.newSubscription<Trade>(channel) { it, sub ->
            val data = it.asJsonObject["tick"].asJsonObject["data"].asJsonArray[0].asJsonObject
            sub.deliver(
                Trade(
                    symbol,
                    data["tradeId"].asLong.toString(),
                    date(data["ts"].asLong.toString()),
                    size(data["amount"], symbol),
                    price(data["price"], symbol),
                    orderSide(data["direction"].asString)
                )
            )
        }.subscribe()
    }

    /**
     * 订阅K线数据
     *
     * 一旦K线数据产生，Websocket服务器将通过此订阅主题接口推送至客户端：
     *
     * @param symbol Symbol
     * @param period KlinePeriodEnum
     * @return Subscription<Kline>
     */
    override suspend fun subscribeKline(symbol: Symbol, period: KlinePeriodEnum): Subscription<Kline> {
        val ch = "market.${symbol.nameWithoutSlash()}.kline.${string(period)}"
        return dispatcher.newSubscription<Kline>(ch) { it, sub ->
            val tick = it.asJsonObject["tick"].asJsonObject
            sub.deliver(
                Kline(
                    symbol,
                    date(it.asJsonObject["ts"].asLong.toString()),
                    price(tick["open"], symbol),
                    price(tick["close"], symbol),
                    price(tick["high"], symbol),
                    price(tick["low"], symbol),
                    volume(tick["vol"], symbol)
                )
            )
        }.subscribe()
    }

    suspend fun getTimestamp(): Long {
        val resp = get(publicUrl("/v1/common/timestamp"))
        return jsonObject(resp)["data"].asLong
    }
}