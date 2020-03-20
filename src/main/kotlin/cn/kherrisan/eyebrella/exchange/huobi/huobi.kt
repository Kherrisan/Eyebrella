package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.Exchange
import cn.kherrisan.eyebrella.core.common.ExchangeName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Huobi @Autowired constructor(
    spot: HuobiSpot,
    adaptor: HuobiServiceDataAdaptor
) : Exchange(ExchangeName.HUOBI, spot, adaptor)