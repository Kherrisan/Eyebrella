package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.common.ExchangeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class BinanceService : ExchangeService() {

    @Autowired
    override lateinit var metaInfo: BinanceMetaInfo

    @Autowired
    @Lazy
    override lateinit var spotMarketService: BinanceSpotMarketService

    @Autowired
    @Lazy
    override lateinit var spotTradingService: BinanceSpotTradingService

    @Autowired
    @Lazy
    override lateinit var marginTradingService: BinanceMarginTradingService
}