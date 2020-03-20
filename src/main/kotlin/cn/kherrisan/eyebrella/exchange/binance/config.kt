package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.common.ExchangeMetaInfo
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
class BinanceMetaInfo : ExchangeMetaInfo()

@Component
class BinanceStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.BINANCE) {
    override var spotMarketHttpHost: String = "https://api.binance.com"
    override var spotTradingHttpHost: String = "https://api.binance.com"
    override var spotMarketWsHost: String = "wss://stream.binance.com:9443/ws/stream1"
}

@ConfigurationProperties(prefix = "eyebrella.exchange.binance")
@Configuration
class BinanceRuntimeConfig : ExchangeRuntimeConfig()