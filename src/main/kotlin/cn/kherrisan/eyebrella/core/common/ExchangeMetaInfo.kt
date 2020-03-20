package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.entity.*

open class ExchangeMetaInfo() {

    lateinit var currencyList: List<Currency>
    lateinit var marginMetaInfo: MutableMap<Symbol, MarginInfo>
    val symbolMetaInfo: MutableMap<Symbol, SymbolMetaInfo> = HashMap()
    val currencyMetaInfo: MutableMap<Currency, CurrencyMetaInfo> = HashMap()
}