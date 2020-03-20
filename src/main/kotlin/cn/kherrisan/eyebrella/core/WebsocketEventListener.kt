package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.core.websocket.WebsocketClient

interface WebsocketEventListener {

    fun onConnected(ws: WebsocketClient)

    fun onHandShaked(ws: WebsocketClient)

    fun onAuthenticated(ws: WebsocketClient)

}