package cn.kherrisan.eyebrella.core

import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import java.lang.Exception

abstract class AbstractBootstraper {

    protected val backgroundContext = CoroutineScope(Job())

    protected val logger = LogManager.getLogger()

    protected abstract suspend fun CoroutineScope.start()

    fun boot() {
        try {
            runBlocking {
                start()
            }
        } catch (e: Exception) {
            println(e)
        }
        logger.debug("Bootstrap 完成")
    }
}