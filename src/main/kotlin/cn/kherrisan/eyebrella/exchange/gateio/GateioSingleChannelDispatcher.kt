package cn.kherrisan.eyebrella.exchange.gateio

class GateioSingleChannelDispatcher(staticConfiguration: GateioStaticConfiguration, val ch: String, runtimeConfig: GateioRuntimeConfig) :
        GateioSpotMarketWebsocketDispatcher(staticConfiguration, runtimeConfig) {
}