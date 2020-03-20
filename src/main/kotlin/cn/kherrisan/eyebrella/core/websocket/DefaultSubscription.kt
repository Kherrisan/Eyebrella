package cn.kherrisan.eyebrella.core.websocket

import com.google.gson.JsonElement
import io.vertx.core.Future
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.delay
import org.apache.commons.collections.buffer.CircularFifoBuffer
import org.apache.logging.log4j.LogManager

open class DefaultSubscription<T : Any>(
        val channel: String,
        val dispatcher: AbstractWebsocketDispatcher,
        var resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit)
    : AbstractSubscription<T>() {

    lateinit var requestPacket: () -> String
    lateinit var subPacket: () -> String
    lateinit var unsubPacket: () -> String
    val buffer = CircularFifoBuffer(128)
    private val logger = LogManager.getLogger(this::class.java)

    override suspend fun unsubscribe() {
        logger.debug("Start to unsubscribe $channel")
        // register the unsubscribe response promise
        val future = Future.future<Any> { unsubscribePromise = it }
        // send the unsubscribe packet
        dispatcher.send(unsubPacket())
        future.await()
        isSubscribed = false
        logger.debug("Unsubscribed to $channel")
        buffer.clear()
    }

    override suspend fun request(): DefaultSubscription<T> {
        logger.debug("Start to request $channel")
        // register the unsubscribe response promise
        val future = Future.future<T> { requestPromise = it }
        // send the unsubscribe packet
        dispatcher.send(requestPacket())
        // waiting 1000ms for the upcoming unsub response packet
        delay(1000L)
        future.await()
        logger.debug("Get response")
        return this
    }

    override suspend fun subscribe(): DefaultSubscription<T> {
        if (isSubscribed) {
            return this
        }
        logger.debug("Start to subscribe $channel")
        buffer.clear()
        // register the unsubscribe response promise
        val future = Future.future<Any> { subscribePromise = it }
        // send the unsubscribe packet
        dispatcher.send(subPacket())
        // waiting for the upcoming unsub response packet
        future.await()
        isSubscribed = true
        logger.debug("Subscribed to $channel")
        return this
    }

    override suspend fun receive(): T = subscriptionChannel.receive()

    override fun isEmpty(): Boolean = subscriptionChannel.isEmpty
}