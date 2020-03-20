package cn.kherrisan.eyebrella.core.common

import io.vertx.core.Vertx
import io.vertx.core.impl.VertxInternal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

fun Vertx.workerDispatcher(): CoroutineDispatcher {
    return (this as VertxInternal).workerPool.asCoroutineDispatcher()
}

object VertxContainer {
    private val instance: Vertx = Vertx.vertx()

    fun vertx(): Vertx = instance
}

/**
 * Vertx 工厂类
 */
@Component
class VertxFactory {

    /**
     * vertx 的工厂方法
     *
     * 全局使用这一个 vertx 对象
     * @return Vertx
     */
    @Bean
    fun vertx(): Vertx = VertxContainer.vertx()
}