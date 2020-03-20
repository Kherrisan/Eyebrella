package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.common.ExchangeMetaInfo
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
class OkexMetaInfo : ExchangeMetaInfo()

@ConfigurationProperties(prefix = "eyebrella.exchange.okex")
@Configuration
class OkexRuntimeConfig : ExchangeRuntimeConfig()

@Component
class OkexStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.OKEX) {
    override var spotMarketHttpHost: String = "https://www.okex.com"
    override var spotTradingHttpHost: String = "https://www.okex.com"
    override var spotMarketWsHost: String = "wss://real.okex.com:8443/ws/v3"
}