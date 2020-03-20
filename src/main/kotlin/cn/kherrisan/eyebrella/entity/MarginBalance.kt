package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.common.Open
import java.math.BigDecimal
import java.util.*

/**
 * 杠杆账户余额
 *
 * @property symbol Symbol 交易对（仓）
 * @property currencyBalance Map<Currency, MarginCurrencyBalance> 可借还的币种
 * @property flatPrice BigDecimal 强制平仓价格
 * @property riskRate BigDecimal 风险率
 * @property time Date 时间
 * @constructor
 */
@Open
data class MarginBalance(
        var symbol: Symbol,
        var currencyBalance: MutableMap<Currency, MarginCurrencyBalance>,
        var flatPrice: BigDecimal,
        var riskRate: BigDecimal,
        val time: Date = MyDate()
) {
    constructor() : this(EMPTY_SYMBOL, mutableMapOf(), 0f.toBigDecimal(), 0f.toBigDecimal())

    var base: MarginCurrencyBalance
        get() {
            if (!currencyBalance.containsKey(symbol.base)) {
                currencyBalance[symbol.base] = MarginCurrencyBalance(symbol.base, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
            }
            return currencyBalance[symbol.base]!!
        }
        set(value) {
            if (symbol.base != value.currency) {
                error("Unmatched symbol in MarginCurrencyBalance")
            }
            currencyBalance[symbol.base] = value
        }

    var quote: MarginCurrencyBalance
        get() {
            if (!currencyBalance.containsKey(symbol.quote)) {
                currencyBalance[symbol.quote] = MarginCurrencyBalance(symbol.quote, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
            }
            return currencyBalance[symbol.quote]!!
        }
        set(value) {
            if (symbol.quote != value.currency) {
                error("Unmatched symbol in MarginCurrencyBalance")
            }
            currencyBalance[symbol.quote] = value
        }
}

/**
 * 币种账户余额
 *
 * @property currency Currency 币种
 * @property available BigDecimal 可用余额
 * @property frozen BigDecimal 冻结余额
 * @property loaned BigDecimal 已借未还余额
 * @property interest BigDecimal 利息未还余额
 * @constructor
 */
data class MarginCurrencyBalance(
        var currency: Currency,
        var available: BigDecimal,
        var frozen: BigDecimal,
        var loaned: BigDecimal,
        var interest: BigDecimal
) {
    constructor() : this(EMPTY_CURRENCY, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
}