package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.D_FORMAT
import cn.kherrisan.eyebrella.core.common.POST
import cn.kherrisan.eyebrella.core.common.sortedUrlEncode
import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.http.HttpMediaTypeEnum
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
import java.math.RoundingMode
import java.util.*

@Component
class HuobiMarginTradingService @Autowired constructor(
    staticConfig: HuobiStaticConfiguration,
    dataAdaptor: HuobiServiceDataAdaptor,
    val metaInfo: HuobiMetaInfo
) : AbstractMarginTradingService(
    staticConfig,
    dataAdaptor,
    HuobiAuthenticateService(staticConfig.marginTradingHttpHost)
) {

    @Autowired
    private lateinit var spot: HuobiSpotTradingService

    override fun string(date: Date): String {
        return D_FORMAT.format(date)
    }

    override fun bigDecimal(e: JsonElement): BigDecimal {
        return e.asString.toBigDecimal()
    }

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val obj = JsonParser.parseString(resp.bodyAsString()).asJsonObject
        if (obj.has("status") && obj["status"].asString == "error") {
            logger.error(obj)
            error(obj)
        }
        return obj
    }

    private fun buildSignedSubpath(
        subPath: String,
        method: String,
        params: MutableMap<String, Any> = mutableMapOf()
    ): String {
        auth().signedHttpRequest(method, subPath, params, mutableMapOf())
        return "$subPath?${sortedUrlEncode(params)}"
    }

    override suspend fun loan(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        if (symbol == CROSS) {
            throw NotImplementedError()
        }
        val params = mutableMapOf<String, Any>(
            "symbol" to string(symbol),
            "currency" to string(currency),
            "amount" to amount.setScale(3, RoundingMode.DOWN)
        ) //huobi 直接要求 3 位小数
        val authUrl = buildSignedSubpath("/v1/margin/orders", POST, params)
        val obj = jsonObject(signedJsonPost(authUrl(authUrl), params))
        return TransactionResult(obj["data"].asLong.toString(), obj["status"].asString == "ok")
    }

    override suspend fun repay(oid: String, currency: Currency, symbol: Symbol, amount: BigDecimal): TransactionResult {
        val params = mutableMapOf<String, Any>("amount" to amount)
        val authUrl = buildSignedSubpath("/v1/margin/orders/$oid/repay", POST, params)
        val obj = jsonObject(signedJsonPost(authUrl(authUrl), params))
        return TransactionResult(obj["data"].asLong.toString())
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
        val params = mutableMapOf<String, String>("amount" to string(symbol))
        size?.let { params["size"] = it.toString() }
        start?.let { params["start-date"] = D_FORMAT.format(it) }
        end?.let { params["end-date"] = D_FORMAT.format(it) }
        status.let { params["states"] = string(it) }
        val obj = jsonObject(signedGet(authUrl("/v1/margin/loan-orders"), params))
        return obj["data"].asJsonArray.map { it.asJsonObject }
            .map {
                val c = currency(it["currency"])
                val lo = LoanOrder(
                    it["id"].asString,
                    symbol,
                    c,
                    size(it["loan-amount"], c),
                    0f.toBigDecimal(),
                    date(it["created-at"]),
                    it["interest-rate"].asString.toBigDecimal(),
                    size(it["interest-amount"], c),
                    0f.toBigDecimal(),
                    date(it["accrued-at"]),
                    loanState(it["state"].asString)
                )
                lo.paidAmount = lo.amount - size(it["loan-balance"], c)
                lo.paidInterest = lo.interest - size(it["interest-balance"], c)
                lo
            }
    }

    override suspend fun getBalance(): Map<Symbol, MarginBalance> {
        val obj = jsonObject(signedGet(authUrl("/v1/margin/accounts/balance")))
        val map = mutableMapOf<Symbol, MarginBalance>()
        obj["data"].asJsonArray.map { it.asJsonObject }
            .forEach {
                val sym = symbol(it["symbol"])
                if (!map.containsKey(sym)) {
                    map[sym] = MarginBalance()
                    map[sym]!!.symbol = symbol(it["symbol"])
                }
                val b = map[sym]!!
                b.base.currency = sym.base
                b.quote.currency = sym.quote
                b.flatPrice = price(it["fl-price"], sym)
                b.riskRate = price(it["risk-rate"].asString.toBigDecimal().stripTrailingZeros(), sym)
                it["list"].asJsonArray.map { it.asJsonObject }
                    .forEach {
                        val c = currency(it["currency"])
                        when (it["type"].asString) {
                            "trade" -> if (c == sym.base) {
                                b.base.available = size(it["balance"], c)
                            } else {
                                b.quote.available = size(it["balance"], c)
                            }
                            "frozen" -> if (c == sym.base) {
                                b.base.frozen = size(it["balance"], c)
                            } else {
                                b.quote.frozen = size(it["balance"], c)
                            }
                            "loan" -> if (c == sym.base) {
                                b.base.loaned = size(it["balance"], c).abs()
                            } else {
                                b.quote.loaned = size(it["balance"], c).abs()
                            }
                            "interest" -> if (c == sym.base) {
                                b.base.interest = size(it["balance"], c).abs()
                            } else {
                                b.quote.interest = size(it["balance"], c).abs()
                            }
                        }
                    }
            }
        return map
    }

    override suspend fun getMarginInfo(): Map<Symbol, MarginInfo> {
        val obj = jsonObject(signedGet(authUrl("/v1/margin/loan-info")))
        val map = mutableMapOf<Symbol, MarginInfo>()
        obj["data"].asJsonArray.map { it.asJsonObject }
            .forEach {
                val sym = symbol(it["symbol"])
                if (!map.containsKey(sym)) {
                    map[sym] = MarginInfo()
                    map[sym]!!.symbol = sym
                }
                val mi = map[sym]!!
                var baseE: JsonObject
                val quoteE: JsonObject
                val cs = it["currencies"].asJsonArray.map { it.asJsonObject }
                if (cs[0].asJsonObject["currency"].asString == sym.base.name) {
                    baseE = cs[0].asJsonObject
                    quoteE = cs[1].asJsonObject
                } else {
                    baseE = cs[1].asJsonObject
                    quoteE = cs[0].asJsonObject
                }
                mi.base.currency = currency(baseE["currency"])
                mi.base.interestRate = baseE["interest-rate"].asString.toBigDecimal().stripTrailingZeros()
                mi.base.loanableAmount = size(baseE["loanable-amt"], mi.base.currency)
                mi.base.minLoanableAmount = size(baseE["min-loan-amt"], mi.base.currency)
                mi.quote.currency = currency(quoteE["currency"])
                mi.quote.interestRate = quoteE["interest-rate"].asString.toBigDecimal().stripTrailingZeros()
                mi.quote.loanableAmount = size(quoteE["loanable-amt"], mi.quote.currency)
                mi.quote.minLoanableAmount = size(quoteE["min-loan-amt"], mi.quote.currency)
            }
        //暂不处理全仓杠杆的情况
        return map
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return spot.limitBuy(symbol, price, amount)
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        return spot.limitSell(symbol, price, amount)
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal?, volume: BigDecimal?): TransactionResult {
        return spot.marketBuy(symbol, amount, volume)
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult {
        return spot.marketSell(symbol, amount)
    }

    override suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder {
        return spot.getOrderDetail(oid, symbol)
    }

    override suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder> {
        return spot.getOpenOrders(symbol, size)
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult {
        return spot.cancelOrder(oid, symbol)
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
        return spot.getOrders(symbol, start, end, state, size)
    }

    override suspend fun transferToSpot(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        if (symbol == CROSS) {
            throw NotImplementedError()
        }
        val params = postBody(
            "currency" to string(currency),
            "amount" to amount,
            "symbol" to string(symbol)
        )
        val authUrl = buildSignedSubpath("/v1/dw/transfer-out/margin", POST)
        val resp = post(authUrl(authUrl), HttpMediaTypeEnum.JSON, params)
        val obj = jsonObject(resp)
        return TransactionResult(
            obj["data"].asLong.toString(),
            obj["status"].asString == "ok"
        )
    }
}