package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.core.service.AbstractSpotTradingService
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.SpotBalance
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class BinanceSpotTradingService @Autowired constructor(
    staticConfiguration: BinanceStaticConfiguration,
    dataAdaptor: BinanceServiceDataAdaptor,
    authenticateService: BinanceAuthenticateService
) : AbstractSpotTradingService(staticConfiguration, dataAdaptor, authenticateService) {

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val e = JsonParser.parseString(resp.bodyAsString())
        if (e.isJsonObject && e.asJsonObject.has("msg")) {
            logger.error(e)
            error(e)
        }
        return e
    }

    /**
     * 现货交易通用的创建订单的方法
     *
     * Binance支持针对下单响应的多种方式：立即返回、订单完成后返回。这里采用立即返回模式。
     *
     * @param symbol Symbol
     * @param price BigDecimal
     * @param amount BigDecimal
     * @param side OrderSideEnum
     * @param type OrderTypeEnum
     * @return TransactionResult
     */
    override suspend fun createOrder(
        symbol: Symbol,
        price: BigDecimal,
        amount: BigDecimal,
        side: OrderSideEnum,
        type: OrderTypeEnum
    ): TransactionResult {
        val params = mutableMapOf<String, Any>(
            "symbol" to string(symbol),
            "side" to string(side),
            "type" to string(type),
            "quantity" to amount.toString(),
            "newOrderRespType" to "ACK" // binance的下单api提供三种响应ACK、RESULT和FULL
        )
        if (price != BigDecimal.ZERO) {
            params["timeInForce"] = "GTC"
            params["price"] = price.toString()
        }
        val resp = signedUrlencodedPost(authUrl("/api/v3/order"), params)
        val obj = jsonObject(resp)
        return TransactionResult(obj["orderId"].asString)
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.BUY, OrderTypeEnum.LIMIT)
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.SELL, OrderTypeEnum.LIMIT)
    }

    /**
     *
     * @param symbol Symbol
     * @param amount BigDecimal base的数量
     * @return TransactionResult
     */
    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal?, volume: BigDecimal?): TransactionResult {
        return createOrder(symbol, BigDecimal.ZERO, amount!!, OrderSideEnum.BUY, OrderTypeEnum.MARKET)
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, BigDecimal.ZERO, amount, OrderSideEnum.SELL, OrderTypeEnum.MARKET)
    }

    override suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder {
        val params = mutableMapOf("symbol" to string(symbol), "orderId" to oid)
        val resp = signedGet(authUrl("/api/v3/order"), params)
        val it = jsonObject(resp)
        return SpotOrder(
            staticConfig.name,
            oid,
            symbol,
            MyDate(it["time"].asLong),
            size(it["origQty"], symbol),
            price(it["price"], symbol),
            orderSide(it["side"].asString),
            orderType(it["type"].asString),
            orderState(it["status"].asString),
            false
        )
    }

    /**
     * 获得所有未完成订单
     *
     * Binance默认会返回所有订单，因此本程序会对响应做截取处理
     *
     * @param symbol Symbol
     * @param size Int
     * @return List<SpotOrder>
     */
    override suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder> {
        val resp = signedGet(
            authUrl("/api/v3/openOrders"),
            mutableMapOf("symbol" to string(symbol))
        )
        val arr = jsonArray(resp)
        val orders = arr.map { it.asJsonObject }
            .map {
                SpotOrder(
                    staticConfig.name,
                    it["orderId"].asString,
                    symbol,
                    MyDate(it["time"].asLong),
                    size(it["origQty"], symbol),
                    price(it["price"], symbol),
                    orderSide(it["side"].asString),
                    orderType(it["type"].asString),
                    orderState(it["status"].asString)
                )
            }.sortedBy { it.time }
        return if (orders.size <= size)
            orders
        else {
            orders.subList(orders.size - size, orders.size)
        }
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult {
        val params = mutableMapOf<String, Any>(
            "symbol" to string(symbol),
            "orderId" to oid
        )
        val resp = signedUrlencodedDelete(authUrl("/api/v3/order"), params)
        return TransactionResult(jsonObject(resp)["orderId"].asString)
    }

    /**
     * 获得现货交易手续费
     *
     * 不知道是不是服务器代码过于老旧，在发送请求（url-encoded）时，参数signature必须放在最后一个。
     *
     * @param symbol Symbol
     * @return TradingFee
     */
    override suspend fun getFee(symbol: Symbol): SpotTradingFee {
        val params = mutableMapOf(
            "timestamp" to MyDate().time.toString(),
            "symbol" to string(symbol)
        )
        val resp = signedGet(authUrl("/wapi/v3/tradeFee.html"), params)
        val obj = jsonObject(resp)["tradeFee"].asJsonArray
            .find { it.asJsonObject["symbol"].asString == string(symbol) }!!
            .asJsonObject
        return SpotTradingFee(staticConfig.name, symbol, obj["maker"].asBigDecimal, obj["taker"].asBigDecimal)
    }

    /**
     * 查询所有历史订单
     *
     * Binance不支持按照state进行查询，所以本程序会对响应结果做截取以满足state参数的要求
     *
     * @param symbol Symbol
     * @param start Date
     * @param end Date
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
        val params = mutableMapOf(
            "symbol" to string(symbol),
            "startTime" to start.time.toString(),
            "endTime" to end.time.toString(),
            "limit" to size.toString()
        )
        val resp = signedGet(authUrl("/api/v3/allOrders"), params)
        var arr = jsonArray(resp).map { it.asJsonObject }.map {
            SpotOrder(
                staticConfig.name,
                it["orderId"].asString,
                symbol,
                MyDate(it["time"].asLong),
                size(it["origQty"], symbol),
                price(it["price"], symbol),
                orderSide(it["side"].asString),
                orderType(it["type"].asString),
                orderState(it["status"].asString)
            )
        }
        if (state != null) {
            arr = arr.filter { it.state == state }
        }
        return arr.sortedBy { it.time }
    }

    override suspend fun transferToMargin(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        return super.transferToMargin(currency, amount, symbol)
    }

    override suspend fun transferToFuture(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        return super.transferToFuture(currency, amount, symbol)
    }

    override suspend fun getBalance(): Map<Currency, SpotBalance> {
        val resp = signedGet(authUrl("/api/v3/account"))
        val map = mutableMapOf<Currency, SpotBalance>()
        jsonObject(resp)["balances"].asJsonArray.map { it.asJsonObject }
            .forEach {
                val c = currency(it["asset"])
                map[c] = SpotBalance(
                    staticConfig.name,
                    c,
                    size(it["free"], c),
                    size(it["locked"], c),
                    MyDate()
                )
            }
        return map
    }
}