package cn.kherrisan.eyebrella.core.websocket

interface Subscription<T : Any> {

    fun isEmpty(): Boolean

    suspend fun subscribe(): Subscription<T>

    suspend fun unsubscribe()

    suspend fun receive(): T

    suspend fun request(): Subscription<T>
}