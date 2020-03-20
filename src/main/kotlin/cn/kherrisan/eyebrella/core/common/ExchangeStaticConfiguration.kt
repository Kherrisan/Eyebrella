package cn.kherrisan.eyebrella.core.common

open class ExchangeStaticConfiguration(val name: ExchangeName) {
    open var spotMarketHttpHost = ""
    open var spotTradingHttpHost = ""
    open var spotMarketWsHost = ""
    open var spotTradingWsHost = ""
    open var marginTradingHttpHost = ""
    open var futureMarketHttpHost = ""
}