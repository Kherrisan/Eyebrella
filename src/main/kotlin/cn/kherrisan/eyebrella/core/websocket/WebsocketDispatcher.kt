package cn.kherrisan.eyebrella.core.websocket

import com.google.gson.JsonElement

interface WebsocketDispatcher {

    fun triggerSubscribedEvent(ch: String)

    /**
     * 触发取消订阅事件
     *
     * 触发 subscription 的 triggerUnsubscribedEvent，来完成 promise。没有启动子协程，不需要 CoroutineScope
     * @param ch String
     */
    suspend fun triggerUnsubscribedEvent(ch: String)

    suspend fun triggerRequestedEvent(ch: String)

    /**
     * 用于突发的断线重连，服务器宕机等情况发生时，进行重启，并重新订阅所有的内容。
     */
    suspend fun restart()

    suspend fun reconnect()

    suspend fun connect()

    suspend fun handleChannelEvents()

    /**
     * 关闭该 dispatcher，并回收相关的资源。
     *
     * 子类可以复写该方法，但是一定要记得要在最后调用父类方法。
     */
    suspend fun close()

    suspend fun dispatch(bytes: ByteArray)

    /**
     * 发送 pong 帧
     *
     * pong 帧的格式符合 RFC6455 的要求，目前只有 Binance 用到了这个方法。
     *
     * @param text String
     */
    fun sendPong(text: String)

    /**
     * 向 websocket 发送消息
     *
     * @receiver CoroutineScope
     * @param text String
     */
    suspend fun send(text: String)

    fun <T : Any> newSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit = { _, _ -> }): DefaultSubscription<T>
}