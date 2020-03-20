package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.core.service.AbstractMarginTradingService
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
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
class OkexMarginTradingService @Autowired constructor(
    staticConfiguration: OkexStaticConfiguration,
    dataAdaptor: OkexServiceDataAdaptor
) : AbstractMarginTradingService(
    staticConfiguration,
    dataAdaptor,
    OkexAuthenticateService(staticConfiguration.marginTradingHttpHost)
) {

    @Autowired
    private lateinit var spot: OkexSpotTradingService

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val obj = JsonParser.parseString(resp.bodyAsString())
        if (resp.statusCode() != 200) {
            logger.error(obj)
            error(obj)
        }
        if (obj.isJsonObject && obj.asJsonObject.has("result") && !obj.asJsonObject["result"].asBoolean) {
            logger.error(obj)
            error(obj)
        }
        return obj
    }

    override suspend fun loan(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        if (symbol == CROSS) {
            throw NotImplementedError()
        }
        val params = postBody(
            "instrument_id" to string(symbol),
            "currency" to string(currency),
            "amount" to amount.toString()
        )
        val obj = jsonObject(signedJsonPost(authUrl("/api/margin/v3/accounts/borrow"), params))
        return TransactionResult(obj["borrow_id"].asString, obj["result"].asBoolean)
    }

    override suspend fun repay(oid: String, currency: Currency, symbol: Symbol, amount: BigDecimal): TransactionResult {
        if (symbol == CROSS) {
            throw NotImplementedError()
        }
        val params = postBody(
            "borrow_id" to oid,
            "instrument_id" to string(symbol),
            "currency" to string(currency),
            "amount" to amount.toString()
        )
        val obj = jsonObject(signedJsonPost(authUrl("/api/margin/v3/accounts/repayment"), params))
        return TransactionResult(obj["repayment_id"].asString, obj["result"].asBoolean)
    }

    override suspend fun getLoanOrders(
        symbol: Symbol,
        size: Int?,
        start: Date?,
        end: Date?,
        status: LoanStatusEnum,
        currency: Currency?
    ): List<LoanOrder> {
        if (symbol == CROSS) {
            throw NotImplementedError()
        }
        val params = getBody(
            "limit" to size?.toString(),
            "status" to string(status)
        )
        val resp = signedGet(authUrl("/api/margin/v3/accounts/${string(symbol)}/borrowed"), params)
        return jsonArray(resp).map { it.asJsonObject }
            .map {
                val c = currency(it["currency"])
                val sym = symbol(it["instrument_id"])
                val lo = LoanOrder(
                    it["borrow_id"].asString,
                    sym,
                    c,
                    size(it["amount"], c),
                    size(it["returned_amount"], c),
                    date(it["timestamp"]),
                    it["rate"].asString.toBigDecimal(),
                    size(it["interest"], c),
                    size(it["paid_interest"], c),
                    date(it["last_interest_time"]),
                    LoanStatusEnum.ACCRUAL
                )
                if (lo.interest.compareTo(lo.paidInterest) == 0) {
                    lo.status = LoanStatusEnum.REPAYED
                }
                lo
            }

    }

    override suspend fun getBalance(): Map<Symbol, MarginBalance> {
        val resp = signedGet(authUrl("/api/margin/v3/accounts"))
        val arr = jsonArray(resp)
        val map = mutableMapOf<Symbol, MarginBalance>()
        arr.map { it.asJsonObject }.forEach {
            val sym = symbol(it["instrument_id"])
            val flatPrice = price(it["liquidation_price"], sym)
            var riskRate = it["risk_rate"].asString
            if (riskRate.isEmpty()) {
                riskRate = "0"
            }
            val baseObj = it["currency:${sym.base.toUpperCase()}"].asJsonObject
            val bmb = MarginCurrencyBalance(
                sym.base,
                size(baseObj["available"], sym.base),
                size(baseObj["frozen"], sym.base),
                size(baseObj["borrowed"], sym.base),
                size(baseObj["lending_fee"], sym.base)
            )
            val quoteObj = it["currency:${sym.quote.toUpperCase()}"].asJsonObject
            val qmb = MarginCurrencyBalance(
                sym.quote,
                size(quoteObj["available"], sym.quote),
                size(quoteObj["frozen"], sym.quote),
                size(quoteObj["borrowed"], sym.quote),
                size(quoteObj["lending_fee"], sym.quote)
            )
            map[sym] =
                MarginBalance(sym, mutableMapOf(sym.base to bmb, sym.quote to qmb), flatPrice, riskRate.toBigDecimal())
        }
        return map
    }

    override suspend fun getMarginInfo(): Map<Symbol, MarginInfo> {
        val resp = signedGet(authUrl("/api/margin/v3/accounts/availability"))
        val arr = jsonArray(resp)
        val map = mutableMapOf<Symbol, MarginInfo>()
        arr.map { it.asJsonObject }.forEach {
            val sym = symbol(it["instrument_id"])
            val base = it["currency:${sym.base.toUpperCase()}"].asJsonObject
            val quote = it["currency:${sym.quote.toUpperCase()}"].asJsonObject
            val bci = MarginCurrencyInfo(
                sym.base,
                base["rate"].asString.toBigDecimal(),
                0f.toBigDecimal(),
                size(base["available"], sym.base)
            )
            val qci = MarginCurrencyInfo(
                sym.quote,
                quote["rate"].asString.toBigDecimal(),
                0f.toBigDecimal(),
                size(quote["available"], sym.quote)
            )
            map[sym] = MarginInfo(sym, mutableMapOf(sym.base to bci, sym.quote to qci))
        }
        return map
    }

    suspend fun createOrder(
        symbol: Symbol,
        price: BigDecimal,
        amount: BigDecimal,
        side: OrderSideEnum,
        type: OrderTypeEnum
    ): TransactionResult {
        val params = mutableMapOf<String, Any>(
            "type" to string(type).toLowerCase(),
            "side" to string(side).toLowerCase(),
            "instrument_id" to string(symbol),
            "margin_trading" to "2"
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
        val resp = signedJsonPost(authUrl("/api/margin/v3/orders"), params)
        val obj = jsonObject(resp)
        return TransactionResult(obj["order_id"].asString)
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.BUY, OrderTypeEnum.LIMIT)
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, price, amount, OrderSideEnum.SELL, OrderTypeEnum.LIMIT)
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal?, volume: BigDecimal?): TransactionResult {
        return createOrder(
            symbol, 0f.toBigDecimal(), volume
                ?: error("Market buy volume can't be null!"), OrderSideEnum.BUY, OrderTypeEnum.MARKET
        )
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult {
        return createOrder(symbol, 0f.toBigDecimal(), amount, OrderSideEnum.SELL, OrderTypeEnum.MARKET)
    }

    private fun marginOrder(obj: JsonObject): SpotOrder {
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

    override suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder {
        val resp = signedGet(
            authUrl("/api/margin/v3/orders/$oid"),
            mutableMapOf("instrument_id" to string(symbol))
        )
        val obj = jsonObject(resp)
        return marginOrder(obj)
    }

    override suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder> {
        val params = mutableMapOf(
            "instrument_id" to string(symbol),
            "limit" to size.toString()
        )
        val resp = signedGet(authUrl("/api/margin/v3/orders_pending"), params)
        return jsonArray(resp).map { it.asJsonObject }
            .map { marginOrder(it) }
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult {
        val params = mutableMapOf("instrument_id" to string(symbol))

        @Suppress("UNCHECKED_CAST")
        val resp = signedJsonPost(authUrl("/api/margin/v3/cancel_orders/$oid"), params as MutableMap<String, Any>)
        val obj = jsonObject(resp)
        return TransactionResult(obj["order_id"].asString, obj["result"].asBoolean, obj["error_message"].asString)
    }

    override suspend fun getFee(symbol: Symbol): SpotTradingFee {
        return spot.getFee(symbol)
    }

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
        val resp = signedGet(authUrl("/api/margin/v3/orders"), params)
        return jsonArray(resp).map { it.asJsonObject }
            .map { marginOrder(it) }
    }

    override suspend fun transferToFuture(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun transferToSpot(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        val params = postBody(
            "currency" to string(currency),
            "amount" to amount,
            "from" to 5,
            "to" to 1,
            "instrument_id" to string(symbol)
        )
        val obj = jsonObject(signedJsonPost(authUrl("/api/account/v3/transfer"), params))
        return TransactionResult(obj["transfer_id"].asString, obj["result"].asBoolean)
    }
}