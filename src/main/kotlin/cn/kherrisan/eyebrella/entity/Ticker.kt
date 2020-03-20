package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.common.Open
import java.math.BigDecimal
import java.util.*

/**
 * 最近24小时的交易对行情数据
 *
 * @property symbol Symbol 交易对
 * @property amount BigDecimal 以基础币种（base）计量的交易量
 * @property vol BigDecimal 以报价币种(quote)计量的交易量
 * @property open BigDecimal 本阶段开盘价
 * @property close BigDecimal 本阶段最新价
 * @property high BigDecimal 本阶段最高价
 * @property low BigDecimal 本阶段最低价
 * @property bid BigDecimal 当前的最高买价
 * @property ask BigDecimal 当前的最低卖价
 * @constructor
 */
@Open
data class Ticker(
        var symbol: Symbol,
        var amount: BigDecimal,
        var vol: BigDecimal,
        var open: BigDecimal,
        var close: BigDecimal,
        var high: BigDecimal,
        var low: BigDecimal,
        var bid: BigDecimal,
        var ask: BigDecimal,
        val time: Date = MyDate()
)