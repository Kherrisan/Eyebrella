package cn.kherrisan.eyebrella.core.websocket

import io.netty.channel.Channel

class ChannelSubscription(val name: String, val channel: Channel) {

    open fun doSubscribe() {

    }

}