package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.ExchangeService
import cn.kherrisan.eyebrella.core.service.MarginTradingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@Lazy
class KucoinService : ExchangeService() {

    @Autowired
    @Lazy
    override lateinit var spotMarketService: KucoinSpotMarketService

    @Autowired
    @Lazy
    override lateinit var  spotTradingService: KucoinSpotTradingService

    override lateinit var marginTradingService: MarginTradingService
}