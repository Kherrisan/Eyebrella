package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open

val BTC_USDT = Symbol(BTC, USDT)
val ETH_USDT = Symbol(ETH, USDT)
val XRP_USDT = Symbol("xrp", "usdt")
val ETH_BTC = Symbol(ETH, BTC)
val XRP_BTC = Symbol("xrp", "btc")
val BCH_BTC = Symbol("bch", "btc")
val EOS_ETH = Symbol("eos", "eth")
val EMPTY_SYMBOL = Symbol(EMPTY_CURRENCY, EMPTY_CURRENCY)

/**
 * 全仓交易对
 */
val CROSS = object : Symbol("", "") {
    override fun toString(): String = "cross_margin"
}

/**
 * 交易对
 *
 * @property quote String 报价货币，用于说明基准货币价格高低的货币
 * @property base String 基准货币
 * @constructor
 */
@Open
data class Symbol(
    var base: Currency,
    var quote: Currency
) : Commodity {

    constructor(base: String, quote: String) : this(Currency(base.toLowerCase()), Currency(quote.toLowerCase()))

    companion object {

        private val quoteCurrencyList = listOf(BTC, ETH, USDT)

        val ERROR = Symbol(ERROR_COIN, ERROR_COIN)

        val EMPTY = Symbol("EMPTY", "EMPTY")

        /**
         * 根据没有斜杠的字符串生成交易对symbol对象
         *
         * @param str String
         * @return Symbol
         */
        fun parse(str: String, quoteList: List<Currency>): Symbol {
            val lStr = str.toLowerCase()
            for (quote in quoteList) {
                if (lStr.endsWith(quote.name)) {
                    return Symbol(Currency(lStr.removeSuffix(quote.name)), Currency(quote.name))
                }
            }
            throw Exception("Invalid base currency in symbol: $lStr")
        }

        fun parse(str: String): Symbol {
            return parse(str, quoteCurrencyList)
        }

        fun split(str: String, spl: String): Symbol {
            val i = str.indexOf(spl)
            return Symbol(str.substring(0, i), str.substring(i + 1))
        }
    }

    constructor() : this(ETH, BTC)

    fun nameWithoutSlash(): String {
        return "$base$quote".toLowerCase()
    }

    fun name(): String {
        return "$base/$quote".toLowerCase()
    }

    override fun toString(): String {
        return name()
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is Symbol && other.name() == name()
    }

    override fun hashCode(): Int {
        return name().hashCode()
    }
}