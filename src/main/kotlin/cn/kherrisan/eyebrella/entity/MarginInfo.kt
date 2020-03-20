package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open
import java.math.BigDecimal

/**
 * 账户借贷信息
 *
 * @property symbol Symbol 交易对（仓）
 * @property currencyInfo Map<Currency, MarginCurrencyInfo>
 * @property flatPrice BigDecimal 强制平仓价
 * @property riskRate BigDecimal 风险率
 * @constructor
 */
@Open
data class MarginInfo(
        var symbol: Symbol,
        var currencyInfo: MutableMap<Currency, MarginCurrencyInfo>
) {
    constructor() : this(EMPTY_SYMBOL, mutableMapOf())

    var base: MarginCurrencyInfo
        get() {
            if (!currencyInfo.containsKey(symbol.base)) {
                currencyInfo[symbol.base] = MarginCurrencyInfo(symbol.base, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
            }
            return currencyInfo[symbol.base]!!
        }
        set(value) {
            if (symbol.base != value.currency) {
                error("Unmatched symbol in MarginCurrencyInfo")
            }
            currencyInfo[symbol.base] = value
        }

    var quote: MarginCurrencyInfo
        get() {
            if (!currencyInfo.containsKey(symbol.quote)) {
                currencyInfo[symbol.quote] = MarginCurrencyInfo(symbol.quote, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
            }
            return currencyInfo[symbol.quote]!!
        }
        set(value) {
            if (symbol.quote != value.currency) {
                error("Unmatched symbol in MarginCurrencyInfo")
            }
            currencyInfo[symbol.quote] = value
        }
}

/**
 * 币种账户借贷信息
 *
 * @property currency Currency 币种
 * @property interestRate BigDecimal 利息率
 * @property minLoanableAmount BigDecimal 最小可借金额
 * @property loanableAmount BigDecimal 可用可借金额
 * @constructor
 */
data class MarginCurrencyInfo(
        var currency: Currency,
        var interestRate: BigDecimal,
        var minLoanableAmount: BigDecimal,
        var loanableAmount: BigDecimal
) {
    constructor() : this(EMPTY_CURRENCY, 0f.toBigDecimal(), 0f.toBigDecimal(), 0f.toBigDecimal())
}