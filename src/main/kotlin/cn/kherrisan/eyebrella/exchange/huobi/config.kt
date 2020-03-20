package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.ExchangeMetaInfo
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import cn.kherrisan.eyebrella.core.enumeration.AccountTypeEnum
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "eyebrella.exchange.huobi")
@Configuration
class HuobiRuntimeConfig : ExchangeRuntimeConfig()

@Component
class HuobiMetaInfo : ExchangeMetaInfo() {
    lateinit var accountIdMap: Map<AccountTypeEnum, String>
}

@Component
class HuobiStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.HUOBI) {
    override var spotMarketHttpHost: String = "https://api.huobi.pro"
    override var spotTradingHttpHost: String = "https://api.huobi.pro"
    override var spotMarketWsHost: String = "wss://api.huobi.pro/ws"
    override var spotTradingWsHost: String = "wss://api.huobi.pro/ws/v1"
}