package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.entity.Depth
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.data.AutoIncrement
import cn.kherrisan.eyebrella.entity.data.Decimal128
import cn.kherrisan.eyebrella.entity.data.ExchangeDepthItem
import io.vertx.kotlin.coroutines.awaitBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class VertxDepthRepository {

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    suspend fun save(exchange: ExchangeName, depth: Depth, limit: Int = 20) {
        awaitBlocking {
            var exchangeAskItem =
                depth.asks.map { ExchangeDepthItem(0L, exchange, depth.symbol, true, it.price, it.amount, depth.time) }
            var exchangeBidItem =
                depth.bids.map { ExchangeDepthItem(0L, exchange, depth.symbol, false, it.price, it.amount, depth.time) }
            if (limit != 0) {
                exchangeAskItem = exchangeAskItem.takeLast(limit)
                exchangeBidItem = exchangeBidItem.take(limit)
            }
            mongoTemplate.insertAll(exchangeAskItem)
            mongoTemplate.insertAll(exchangeBidItem)
        }
    }
}