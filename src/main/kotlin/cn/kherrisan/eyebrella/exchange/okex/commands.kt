package cn.kherrisan.eyebrella.exchange.okex

data class OkexMarketSubscriptionRequest(
    val op: String,
    val args: List<String>
)

data class OkexMarketSubscriptionResponse(
    val event: String,
    val channel: String
)