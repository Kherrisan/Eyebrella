package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.entity.Symbol

@Open
data class ExchangeRuntimeConfig(
    var proxyHost: String? = null,

    var proxyPort: Int? = null,

    /**
     * api key
     * or access key
     */
    var apiKey: String? = null,

    var secretKey: String? = null,

    var username: String? = null,

    /**
     * password
     * or passphrase
     */
    var password: String? = null,

    var pemPath: String? = null,

    var pingInterval: Int? = null,

    var pingTimeout: Int? = null,

    /**
     * 关注的交易对
     */
    var symbols: List<Symbol>? = null
)