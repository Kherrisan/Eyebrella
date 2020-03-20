package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open

val BTC = Currency("btc")
val ETH = Currency("eth")
val USDT = Currency("usdt")
val ERROR_COIN = Currency("error_coin")
val EMPTY_CURRENCY = Currency("empty")

@Open
data class Currency(
        var name: String
) {


    override fun toString(): String = name.toLowerCase()

    fun toLowerCase(): String = name.toLowerCase()

    fun toUpperCase(): String = name.toUpperCase()

    override fun equals(other: Any?): Boolean {
        return other != null && other is Currency && name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}