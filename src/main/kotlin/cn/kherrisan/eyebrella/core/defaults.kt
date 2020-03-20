package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher

class DefaultWebsocketDispatcher : AbstractWebsocketDispatcher(ExchangeRuntimeConfig()) {
    override val host: String = ""
    override val name: ExchangeName = ExchangeName.HUOBI

    override suspend fun dispatch(bytes: ByteArray) {
        throw NotImplementedError()
    }
}