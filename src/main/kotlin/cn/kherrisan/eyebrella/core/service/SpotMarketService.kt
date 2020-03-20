package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import java.util.*

/**
 * 现货市场行情接口，仅包含对现货产品的报价、成交情况的当前和历史数据的查询，以及实时数据的订阅功能
 */
interface SpotMarketService {

    /**
     * 获得交易所支持的所有交易对
     *
     * @return List<Symbol> 交易对Symbol的列表，按base字母升序排列
     */
    suspend fun getSymbols(): List<Symbol>

    /**
     * 获得交易对的metaInfo
     *
     * @return Map<Symbol, SymbolMetaInfo>
     */
    suspend fun getSymbolMetaInfo(): List<SymbolMetaInfo>

    /**
     * 获得所有币种
     *
     * @return List<Currency> 币种列表，按字母升序排列
     */
    suspend fun getCurrencies(): List<Currency>

    /**
     * 获得某个交易对的深度数据
     *
     * @param symbol Symbol
     * @param size Int 获得的深度个数，默认为10
     * @return Depth
     */
    suspend fun getDepths(symbol: Symbol, size: Int = 20): Depth

    /**
     * 此接口返回历史K线数据。
     *
     * 返回的kline列表按照时间由远至近的顺序排列（即按照time的升序）
     *
     * @param symbol Symbol 交易对
     * @param periodEnum Int 粒度（K线的宽度所覆盖的时间），默认为1分钟
     * @param size Int K线数量，默认为100
     * @param since Long 开始时间，默认为0，即返回最新的size个K线
     */
    suspend fun getKlines(symbol: Symbol, periodEnum: KlinePeriodEnum = KlinePeriodEnum._1MIN, size: Int = 100, since: Date? = null): List<Kline>

    /**
     * 订阅深度数据
     *
     * @param symbol Symbol
     * @return Subscription<Depth>
     */
    suspend fun subscribeDepth(symbol: Symbol): Subscription<Depth>

    /**
     * 订阅成交数据
     *
     * @param symbol Symbol
     * @return Subscription<Trade>
     */
    suspend fun subscribeTrade(symbol: Symbol): Subscription<Trade>

    /**
     * 订阅K线数据
     *
     * @param symbol Symbol
     * @param period KlinePeriodEnum
     * @return Subscription<Kline>
     */
    suspend fun subscribeKline(symbol: Symbol, period: KlinePeriodEnum = KlinePeriodEnum._1MIN): Subscription<Kline>
}