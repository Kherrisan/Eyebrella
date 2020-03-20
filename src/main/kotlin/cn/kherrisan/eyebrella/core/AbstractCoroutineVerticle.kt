package cn.kherrisan.eyebrella.core

import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import java.util.*

abstract class AbstractCoroutineVerticle : CoroutineVerticle() {

    protected val logger = LogManager.getLogger()
    private val jobList: MutableList<Job> = LinkedList()

    override suspend fun stop() {
        cancel()
//        jobList.forEach { it.cancel() }
    }

    fun launchPeriodicalJob(period: Long, immediately: Boolean = false, b: suspend CoroutineScope.() -> Unit) =
        jobList.add(launch {
            while (true) {
                if (immediately) {
                    b()
                    delay(period)
                } else {
                    delay(period)
                    b()
                }
            }
        })

    fun launchJob(b: suspend CoroutineScope.() -> Unit) = jobList.add(launch(block = b))

}