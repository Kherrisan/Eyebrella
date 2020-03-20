package cn.kherrisan.eyebrella.exchange.poloniex

import cn.kherrisan.eyebrella.core.common.ExchangeService
import cn.kherrisan.eyebrella.core.common.SpotTradingService
import cn.kherrisan.eyebrella.core.service.MarginTradingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PoloniexService : ExchangeService() {

    @Autowired
    override lateinit var spotMarketService: PoloniexSpotMarketService

    override lateinit var spotTradingService: SpotTradingService
    override lateinit var marginTradingService: MarginTradingService
}