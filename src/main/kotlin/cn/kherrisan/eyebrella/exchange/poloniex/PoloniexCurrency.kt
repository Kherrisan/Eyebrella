package cn.kherrisan.eyebrella.exchange.poloniex

import cn.kherrisan.eyebrella.entity.Currency

class PoloniexCurrency(name: String, var id: Int) : Currency(name) {
    override fun toString(): String = "$name:$id"
}