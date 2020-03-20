package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.core.Spot
import cn.kherrisan.eyebrella.entity.data.SpotBalance
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.awaitBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class VertxSpotBalanceRepository(val vertx: Vertx) {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var spotBalanceRepository: SpotBalanceRepository

    suspend fun insert(sb: SpotBalance) {
        //spotbalance 不需要 update，如果有新的直接 insert。
        awaitBlocking {
            spotBalanceRepository.save(sb)
        }
    }

    suspend fun getByExchangeAndCurrency(ex: ExchangeName, c: Currency): SpotBalance? {
        return awaitBlocking {
            val query = Query()
            query.addCriteria(Criteria.where("exchange").`is`(ex))
                    .addCriteria(Criteria.where("currency").`is`(c))
                    .with(Sort.by("time"))
                    .limit(1)
            mongoTemplate.findOne(query, SpotBalance::class.java)
        }
    }
}