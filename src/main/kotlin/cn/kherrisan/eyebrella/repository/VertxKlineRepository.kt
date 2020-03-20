package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.entity.Kline
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class VertxKlineRepository {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    suspend fun save(kline: Kline): Kline {
        return awaitBlocking {
            mongoTemplate.insert(kline)
        }
    }
}