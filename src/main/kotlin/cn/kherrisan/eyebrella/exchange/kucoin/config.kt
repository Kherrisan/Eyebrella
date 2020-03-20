package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.ExchangeMetaInfo
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "eyebrella.exchange.kucoin")
@Configuration
class KucoinRuntimeConfig : ExchangeRuntimeConfig()

@Component
class KucoinStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.KUCOIN) {
    override var spotMarketHttpHost: String = "https://api.kucoin.com"
}

@Component
class KucoinMetaInfo : ExchangeMetaInfo()