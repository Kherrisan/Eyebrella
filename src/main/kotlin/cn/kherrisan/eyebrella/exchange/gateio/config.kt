package cn.kherrisan.eyebrella.exchange.gateio

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "eyebrella.exchange.gateio")
@Configuration
class GateioRuntimeConfig : ExchangeRuntimeConfig()

@Component
class GateioStaticConfiguration : ExchangeStaticConfiguration(ExchangeName.GATEIO) {
    override var spotMarketHttpHost: String = "https://data.gateio.life"
    override var spotMarketWsHost: String = "wss://ws.gate.io/v3/"
}