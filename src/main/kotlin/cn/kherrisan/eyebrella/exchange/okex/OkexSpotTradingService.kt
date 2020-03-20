package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotTradingService
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.SpotBalance
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class OkexSpotTradingService @Autowired constructor(
    staticConfiguration: OkexStaticConfiguration,
    dataAdaptor: OkexServiceDataAdaptor
) : AbstractSpotTradingService(
    staticConfiguration,
    dataAdaptor,
    OkexAuthenticateService(staticConfiguration.spotTradingHttpHost)
) {

    @Autowired
    private lateinit var dispatcher: OkexSpotTradingWebsocketDispatcher

    override fun checkResponse(http: HttpResponse<Buffer>): JsonElement {
        val e = JsonParser.parseString(http.bodyAsString())
        if (http.statusCode() != 200) {
            logger.error(e)
            error(e)
        }
        return e
    }

    override suspend fun createOrder(
        symbol: Symbol,
        price: BigDecimal,
        amount: BigDecimal,
        side: OrderSideEnum,
        type: OrderTypeEnum
    ): TransactionResult {
        val params = mutableMapOf<String, Any>(
            "type" to string(type).toLowerCase(),
            "side" to string(side).toLowerCase(),
            "instrument_id" to string(symbol)
        )
        if (type == OrderTypeEnum.LIMIT) {
            params["price"] = price
            params["size"] = amount
        } else if (type == OrderTypeEnum.MARKET) {
            if (side == OrderSideEnum.BUY) {
                params["notional"] = amount
            } else {
                params["size"] = amount
            }
        }
        val resp = signedJsonPost(authUrl("/api/spot/v3/orders"), params)
        val obj = jsonObject(resp)
        return TransactionResult(obj["order_id"].asString)
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.BUY, OrderTypeEnum.LIMIT)
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.SELL, OrderTypeEnum.LIMIT)
    }

    /**
     * 市价买入现货
     *
     * @param symbol Symbol
     * @param amount BigDecimal quote金额
     * @return TransactionResult
     */
    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal?, volume: BigDecimal?): TransactionResult {
        return createOrder(
            symbol, 0f.toBigDecimal(), volume
                ?: error("Market buy volume can't be null!"), OrderSideEnum.BUY, OrderTypeEnum.MARKET
        )
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, 0f.toBigDecimal(), amount, OrderSideEnum.SELL, OrderTypeEnum.MARKET)
    }

    override suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder {
        val resp = signedGet(
            authUrl("/api/spot/v3/orders/$oid"),
            mutableMapOf("instrument_id" to string(symbol))
        )
        val obj = jsonObject(resp)
        return spotOrder(obj)
    }

    override suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder> {
        val params = mutableMapOf(
            "instrument_id" to string(symbol),
            "limit" to size.toString()
        )
        val resp = signedGet(authUrl("/api/spot/v3/orders_pending"), params)
        return jsonArray(resp).map { it.asJsonObject }
            .map { spotOrder(it) }
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult {
        val params = mutableMapOf("instrument_id" to string(symbol))
        val resp = signedJsonPost(authUrl("/api/spot/v3/cancel_orders/$oid"), params as MutableMap<String, Any>)
        val obj = jsonObject(resp)
        return TransactionResult(obj["order_id"].asString, obj["result"].asBoolean, obj["error_message"].asString)
    }

    /**
     * 获得现货交易手续费
     *
     * Okex的现货交易费率不会区分不同的symbol，因此针对不同的symbol，只要调用getFee方法一次就行了。
     *
     * @param symbol Symbol
     * @return TradingFee
     */
    override suspend fun getFee(symbol: Symbol): SpotTradingFee {
        val resp = signedGet(authUrl("/api/spot/v3/trade_fee"))
        val obj = jsonObject(resp)
        return SpotTradingFee(
            staticConfig.name,
            symbol,
            obj["maker"].asString.toBigDecimal(),
            obj["taker"].asString.toBigDecimal(),
            date(obj["timestamp"].asString)
        )
    }

    private fun spotOrder(obj: JsonObject): SpotOrder {
        val sym = symbol(obj["instrument_id"].asString)
        var rawPrice = obj["price"]
        var size = size(obj["size"], sym)
        val orderType = orderType(obj["type"].asString)
        val orderSide = orderSide(obj["side"].asString)
        if (orderType == OrderTypeEnum.MARKET) {
            //市价买入，没有委托价格，且 size 是委托金额，需要计算委托数量
            rawPrice = obj["price_avg"]
            if (orderSide == OrderSideEnum.BUY) {
                val notional = volume(obj["notional"], sym)
                //委托数量 = 委托金额 / 实际成交平均价格
                val si = sizeIncrement(sym)
                val pi = priceIncrement(sym)
                size = size(notional.setScale(si + pi) / price(rawPrice, sym), sym)
            }
        }
        return SpotOrder(
            staticConfig.name,
            obj["order_id"].asString,
            sym,
            date(obj["timestamp"].asString),
            size,
            price(rawPrice, sym),
            orderSide,
            orderType,
            orderState(obj["state"].asString)
        )
    }

    /**
     * 查询所有历史订单
     *
     * Okex不支持根据时间来查询历史订单，所以start和end参数是无效的
     *
     * @param symbol Symbol
     * @param start Date 无效
     * @param end Date 无效
     * @param state OrderStateEnum
     * @param size Int
     * @return List<SpotOrder>
     */
    override suspend fun getOrders(
        symbol: Symbol,
        start: Date,
        end: Date,
        state: OrderStateEnum?,
        size: Int
    ): List<SpotOrder> {
        state ?: error("State for getOrders can't be null.")
        val params = mutableMapOf(
            "instrument_id" to string(symbol),
            "limit" to size.toString(),
            "state" to string(state)
        )
        val resp = signedGet(authUrl("/api/spot/v3/orders"), params)
        return jsonArray(resp).map { it.asJsonObject }
            .map { spotOrder(it) }
    }

    override suspend fun getBalance(): Map<Currency, SpotBalance> {
        val resp = signedGet(authUrl("/api/spot/v3/accounts"))
        val map = mutableMapOf<Currency, SpotBalance>()
        jsonArray(resp).map { it.asJsonObject }
            .forEach {
                val c = currency(it["currency"])
                map[c] = SpotBalance(
                    staticConfig.name,
                    c,
                    size(it["available"], c),
                    size(it["frozen"], c)
                )
            }
        return map
    }

    override suspend fun transferToMargin(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        val params = postBody(
            "currency" to string(currency),
            "amount" to amount,
            "from" to 1,
            "to" to 5,
            "to_instrument_id" to string(symbol)
        )
        val obj = jsonObject(signedJsonPost(authUrl("/api/account/v3/transfer"), params))
        return TransactionResult(obj["transfer_id"].asString, obj["result"].asBoolean)
    }

    override suspend fun transferToFuture(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun subscribeBalance(symbol: Symbol?): Subscription<SpotBalance> {
        return super.subscribeBalance(symbol)
    }

    override suspend fun subscribeOrderDeal(symbol: Symbol?): Subscription<SpotOrderDeal> {
        return super.subscribeOrderDeal(symbol)
    }
}