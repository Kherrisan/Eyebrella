package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.Exchange
import cn.kherrisan.eyebrella.core.common.ExchangeName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Okex @Autowired constructor(
    spot: OkexSpot,
    adaptor: OkexServiceDataAdaptor
) : Exchange(ExchangeName.OKEX, spot, adaptor)