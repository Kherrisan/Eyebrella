package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.service.AbstractMarginTradingService
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
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

/**
 * Binance 杠杆交易类
 *
 * Binance 不支持逐仓交易，所有的操作都要是全仓的，即只支持 symbol 为 CROSS 的调用。
 *
 * @constructor
 */
@Component

class BinanceMarginTradingService @Autowired constructor(
        staticConfig: BinanceStaticConfiguration,
        dataAdaptor: BinanceServiceDataAdaptor,
        authenticateService: BinanceAuthenticateService
) : AbstractMarginTradingService(staticConfig, dataAdaptor, authenticateService) {

    override fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val e = JsonParser.parseString(resp.bodyAsString())
        if (e.isJsonObject && e.asJsonObject.has("msg")) {
            logger.error(e)
            error(e)
        }
        return e
    }

    /**
     * 仓内借币
     *
     * [Binance API](https://binance-docs.github.io/apidocs/spot/cn/#margin-5)
     *
     * @param currency Currency
     * @param amount BigDecimal
     * @param symbol Symbol 只能取 CROSS（即方法的默认值）
     * @return TransactionResult
     */
    override suspend fun loan(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        if (symbol != CROSS) {
            throw NotImplementedError()
        }
        val params = postBody("asset" to string(currency),
                "amount" to amount)
        val resp = signedUrlencodedPost(authUrl("/sapi/v1/margin/loan"), params)
        val obj = jsonObject(resp)
        return TransactionResult(obj["tranId"].asLong.toString())
    }

    /**
     * 仓内还币
     *
     * Binance 现货杠杆的还币不是基于借币订单的，因此参数 oid 可以为空。
     *
     * [Binance API](https://binance-docs.github.io/apidocs/spot/cn/#margin-6)
     *
     * @param oid String 无效，可以为空。
     * @param currency Currency
     * @param symbol Symbol 只能取 CROSS（即方法的默认值）
     * @param amount BigDecimal
     * @return TransactionResult
     */
    override suspend fun repay(oid: String, currency: Currency, symbol: Symbol, amount: BigDecimal): TransactionResult {
        if (symbol != CROSS) {
            throw NotImplementedError()
        }
        val params = postBody("asset" to string(currency),
                "amount" to amount)
        val resp = signedUrlencodedPost(authUrl("/sapi/v1/margin/repay"), params)
        val obj = jsonObject(resp)
        return TransactionResult(obj["tranId"].asLong.toString())
    }

    /**
     * 查询借币订单
     *
     * 同时使用了 [查询借贷 API](https://binance-docs.github.io/apidocs/spot/cn/#user_data-22) 和 [查询还贷 API](https://binance-docs.github.io/apidocs/spot/cn/#user_data-23)
     *
     * @param symbol Symbol
     * @param size Int? 查询订单个数
     * @param start Date? 开始时间
     * @param end Date? 结束时间
     * @param status LoanStatusEnum
     * @param currency Currency?
     * @return List<LoanOrder>
     */

    override suspend fun getLoanOrders(symbol: Symbol, size: Int?, start: Date?, end: Date?, status: LoanStatusEnum, currency: Currency?): List<LoanOrder> {
        if (symbol != CROSS) {
            throw NotImplementedError()
        }
        currency ?: error("Currency for getLoanOrders can't be null.")
//        var params = getBody("asset" to string(currency),
//                "startTime" to start?.time.toString(),
//                "endTime" to end?.time.toString(),
//                "size" to size.toString())
//        val loanResp = signedGet(authUrl("/sapi/v1/margin/loan"))
//        val loan = jsonObject(loanResp)
        //Binance 的借还账单结构和 huobi、okex 完全不同，Binance 不需要借还账单的对应，
        //只需要借多少还多少就可以了。并且计息也不和借贷账单对应。
        throw NotImplementedError()
    }

    /**
     * 查询所有杠杆资产余额
     *
     * 返回全仓下所有币种的余额
     *
     * [Binance API](https://binance-docs.github.io/apidocs/spot/cn/#user_data-26)
     *
     * @return Map<Symbol, MarginBalance> 只有一个 key：CROSS
     */
    override suspend fun getBalance(): Map<Symbol, MarginBalance> {
        val resp = signedGet(authUrl("/sapi/v1/margin/account"))
        val mb = MarginBalance()
        val map = mutableMapOf<Symbol, MarginBalance>(CROSS to mb)
        mb.symbol = CROSS
        val obj = jsonObject(resp)
        obj["userAssets"].asJsonArray.map { it.asJsonObject }
                .forEach {
                    val c = currency(it["asset"])
                    mb.currencyBalance[c] = MarginCurrencyBalance(c,
                            size(it["free"], c),
                            size(it["locked"], c),
                            size(it["borrowed"], c),
                            size(it["interest"], c))
                }
        return map
    }

    /**
     * 查询所有交易对借贷基本信息
     *
     * Binance 不提供查询利率的接口，垃圾。
     *
     * [获取所有杠杆资产信息 (MARKET_DATA)](https://binance-docs.github.io/apidocs/spot/cn/#market_data-3)
     * [查询账户最大可借贷额(USER_DATA)](https://binance-docs.github.io/apidocs/spot/cn/#user_data-31)
     *
     * @return Map<Symbol, MarginInfo>
     */

    override suspend fun getMarginInfo(): Map<Symbol, MarginInfo> {
//        val minLoan = jsonArray(signedGet(authUrl("/sapi/v1/margin/allAssets")))
//        val marginInfo = MarginInfo()
//        marginInfo.symbol = CROSS
        throw NotImplementedError()
//        return mapOf(CROSS to marginInfo)
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal?, volume: BigDecimal?): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder {
        throw NotImplementedError()
    }

    override suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder> {
        throw NotImplementedError()
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun getFee(symbol: Symbol): SpotTradingFee {
        throw NotImplementedError()
    }

    override suspend fun getOrders(symbol: Symbol, start: Date, end: Date, state: OrderStateEnum?, size: Int): List<SpotOrder> {
        throw NotImplementedError()
    }

    override suspend fun transferToSpot(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        return super.transferToSpot(currency, amount, symbol)
    }

    override suspend fun transferToMargin(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        return super.transferToMargin(currency, amount, symbol)
    }
}