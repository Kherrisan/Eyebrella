package cn.kherrisan.eyebrella.core.websocket

import cn.kherrisan.eyebrella.core.common.VertxContainer
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.collections.buffer.CircularFifoBuffer

class SynchronizedSubscription<T : Any>(private val resolver: (List<Any>, AbstractSubscription<T>) -> Unit) : AbstractSubscription<T>() {

    private val vertx = VertxContainer.vertx()
    private val childSubscription = mutableListOf<AbstractSubscription<Any>>()
    private val bufferList = mutableListOf<CircularFifoBuffer>()
    private var jobList = mutableListOf<Job>()

    private suspend fun handleSynchronizeEvents() {
        val dataList = mutableListOf<Any>()
        for (buf in bufferList) {
            if (buf.size == 0) {
                return
            }
            dataList.add(buf.remove())
        }
        resolver(dataList, this)
    }

    fun addChild(sub: AbstractSubscription<Any>): SynchronizedSubscription<T> {
        val buffer = CircularFifoBuffer(2)
        bufferList.add(buffer)
        childSubscription.add(sub)
        jobList.add(launch(vertx.dispatcher()) {
            while (true) {
                try {
                    val data = sub.receive()
                    buffer.add(data)
                } catch (c: CancellationException) {
                    return@launch
                }
            }
        })
        return this
    }

    override suspend fun subscribe(): Subscription<T> {
        if (isSubscribed) {
            return this
        }
        for (ch in childSubscription) {
            ch.subscribe()
        }
        jobList.add(launch(vertx.dispatcher()) {
            while (true) {
                try {
                    handleSynchronizeEvents()
                    delay(10)
                } catch (cancel: CancellationException) {
                    subscriptionChannel.close(cancel)
                    return@launch
                }
            }
        })
        return this
    }

    override suspend fun unsubscribe() {
        for (ch in childSubscription) {
            ch.unsubscribe()
        }
    }

    override suspend fun request(): Subscription<T> {
        throw NotImplementedError()
    }

    override suspend fun receive(): T = subscriptionChannel.receive()

    override fun isEmpty(): Boolean = subscriptionChannel.isEmpty

    fun close() {
        for (job in jobList) {
            job.cancel(CancellationException("E"))
        }
        jobList.clear()
        bufferList.clear()
    }
}