package cn.kherrisan.eyebrella.arbitrage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@ConfigurationProperties(prefix = "eyebrella.arbitrage")
@Configuration
data class ArbitrageConfig(
    var syntheticMax: String? = null,
    var syntheticMin: String? = null
)