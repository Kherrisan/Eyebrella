package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.exchange.binance.BinanceService
import cn.kherrisan.eyebrella.exchange.gateio.GateioService
import cn.kherrisan.eyebrella.exchange.huobi.HuobiService
import cn.kherrisan.eyebrella.exchange.kucoin.KucoinService
import cn.kherrisan.eyebrella.exchange.okex.OkexService
import cn.kherrisan.eyebrella.exchange.poloniex.PoloniexService

enum class ExchangeName(val exchangeServiceClass: Class<out ExchangeService>) {
    HUOBI(HuobiService::class.java),
    BINANCE(BinanceService::class.java),
    OKEX(OkexService::class.java),
    GATEIO(GateioService::class.java),
    KUCOIN(KucoinService::class.java),
    POLONIEX(PoloniexService::class.java)
}