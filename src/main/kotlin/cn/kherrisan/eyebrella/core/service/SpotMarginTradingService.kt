package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import java.math.BigDecimal
import java.util.*

/**
 * 现货交易和杠杆交易公用的交易接口
 *
 * 因为现货交易和杠杆交易是共享的同一套市场行情，很多交易操作接口都是相同的，所以提取出一个公用的接口。
 * 返回值类型是协变的，所以如果现货或者杠杆接口要返回额外的数据，可以重写接口的返回值类型。
 */
interface SpotMarginTradingService {

    /**
     * 提交限价购买的订单
     *
     * @param symbol Symbol 交易对
     * @param price BigDecimal 价格
     * @param amount BigDecimal 数量
     * @return PlaceOrderResult
     */
    suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult

    /**
     * 提交限价出售的订单
     *
     * @param symbol Symbol 交易对
     * @param price BigDecimal 价格
     * @param amount BigDecimal 数量
     * @return PlaceOrderResult
     */
    suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): TransactionResult

    /**
     * 提交市价购买的订单
     *
     * 不用给定价格，交易所的交易系统会自动给出一个最优价格，并进行撮合
     *
     * @param symbol Symbol 交易对
     * @param amount BigDecimal 基础货币的数量
     * @param volume BigDecimal 以计价货币为单位的金额，因为市价单在下单时出价是未知的。
     * @return PlaceOrderResult
     */
    suspend fun marketBuy(symbol: Symbol, amount: BigDecimal? = null, volume: BigDecimal? = null): TransactionResult

    /**
     * 提交市价出售的订单
     *
     * 不用给定价格，交易所的交易系统会自动给出一个最优价格，并进行撮合
     *
     * @param symbol Symbol 交易对
     * @param amount BigDecimal 数量
     * @return PlaceOrderResult
     */
    suspend fun marketSell(symbol: Symbol, amount: BigDecimal): TransactionResult

    /**
     * 查询订单详情
     *
     * @param oid String
     * @param symbol Symbol
     * @return SpotOrder
     */
    suspend fun getOrderDetail(oid: String, symbol: Symbol): SpotOrder

    /**
     * 查询所有未成交订单
     *
     * @return List<SpotOrder>
     */
    suspend fun getOpenOrders(symbol: Symbol, size: Int): List<SpotOrder>

    /**
     * 提交撤销订单的请求
     *
     * 提交请求，具体撤销的结果需要再查询一次getOrderDetail
     *
     * @param oid String 订单id
     * @return OrderResult
     */
    suspend fun cancelOrder(oid: String, symbol: Symbol): TransactionResult

    /**
     * 查询某个交易对的现货交易手续费费率
     *
     * @param symbol Symbol
     * @return TradingFee
     */
    suspend fun getFee(symbol: Symbol): SpotTradingFee

    /**
     * 查询所有历史订单
     *
     * @param symbol Symbol
     * @param start Date 开始时间
     * @param end Date 结束时间
     * @param state OrderStateEnum 订单状态
     * @param size Int 返回的结果数量
     * @return List<SpotOrder>
     */
    suspend fun getOrders(symbol: Symbol, start: Date, end: Date = Date(), state: OrderStateEnum? = null, size: Int = 10): List<SpotOrder>

    /**
     * 划转到杠杆账户
     *
     * @param currency Currency
     * @param amount BigDecimal
     * @param symbol Symbol? 如果不为null，则表示划转到某个交易对的仓位，是逐仓杠杆
     */
    suspend fun transferToMargin(currency: Currency, amount: BigDecimal, symbol: Symbol = CROSS): TransactionResult

    /**
     * 划转到期货账户
     *
     * @param currency Currency
     * @param amount BigDecimal
     */
    suspend fun transferToFuture(currency: Currency, amount: BigDecimal, symbol: Symbol = CROSS): TransactionResult

    /**
     * 转账到现货账户
     *
     * @param currency Currency
     * @param amount BigDecimal
     * @return TransactionResult
     */
    suspend fun transferToSpot(currency: Currency, amount: BigDecimal, symbol: Symbol = CROSS): TransactionResult
}