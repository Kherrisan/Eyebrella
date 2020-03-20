package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.*

@Component
class VertxFeeRepository {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    suspend fun getLatest(exchange: ExchangeName, symbol: Symbol): SpotTradingFee? {
        val q = Query()
        q.addCriteria(Criteria.where("symbol").`is`(symbol))
            .addCriteria(Criteria.where("exchange").`is`(exchange))
            .with(Sort.by(Sort.Direction.DESC, "time"))
        return mongoTemplate.findOne(q, SpotTradingFee::class.java)
    }

    suspend fun save(fee: SpotTradingFee) {
        mongoTemplate.save(fee)
    }

    suspend fun save(fees: List<SpotTradingFee>) {
        mongoTemplate.insertAll(fees)
    }

}