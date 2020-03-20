package cn.kherrisan.eyebrella.exchange.poloniex

import cn.kherrisan.eyebrella.core.common.ExchangeMetaInfo
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "eyebrella.exchange.poloniex")
@Configuration
class PoloniexRuntimeConfig : ExchangeRuntimeConfig()

@Component
class PoloniexStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.POLONIEX) {
    override var spotMarketHttpHost: String = "https://poloniex.com/public"
    override var spotMarketWsHost: String = "wss://api2.poloniex.com"
}

@Component
class PoloniexMetaInfo : ExchangeMetaInfo()