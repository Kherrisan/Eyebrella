package cn.kherrisan.eyebrella.exchange.poloniex

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PoloniexWebsocketDispatcher @Autowired constructor(
        val staticConfiguration: PoloniexStaticConfiguration,
        runtimeConfig: PoloniexRuntimeConfig
) : AbstractWebsocketDispatcher(runtimeConfig) {

    override val host: String = staticConfiguration.spotMarketWsHost
    override val name: ExchangeName = ExchangeName.POLONIEX

    override suspend fun dispatch(bytes: ByteArray) {
        throw NotImplementedError()
    }
}