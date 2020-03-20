package cn.kherrisan.eyebrella.core.websocket

import cn.kherrisan.eyebrella.core.common.ExchangeRuntimeConfig

abstract class SingleChannelWebsocketDispatcher(val channel: String, runtimeConfig: ExchangeRuntimeConfig) :
        AbstractWebsocketDispatcher(runtimeConfig)