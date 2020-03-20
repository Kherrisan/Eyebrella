package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.data.SpotOrder

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.awaitBlocking
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.util.*

const val MONGO_EXCHANGE_SPOT_ORDER = "exchange_spot_order"

@Component
class VertxSpotOrderRepository(val vertx: Vertx) {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    suspend fun getOldest(ex: ExchangeName? = null, symbol: Symbol? = null): SpotOrder? {
        val q = Query()
        ex?.let { q.addCriteria(Criteria.where("exchange").`is`(ex.name)) }
        symbol?.let { q.addCriteria(Criteria.where("symbol").`is`(symbol.name())) }
        q.with(Sort.by(Sort.Direction.ASC, "time"))
        return awaitBlocking {
            mongoTemplate.findOne(q, SpotOrder::class.java)
        }
    }

    suspend fun getNewest(ex: ExchangeName? = null, symbol: Symbol? = null): SpotOrder? {
        val q = Query()
        ex?.let { q.addCriteria(Criteria.where("exchange").`is`(ex.name)) }
        symbol?.let { q.addCriteria(Criteria.where("symbol").`is`(symbol.name())) }
        q.with(Sort.by(Sort.Direction.DESC, "time"))
        return awaitBlocking {
            mongoTemplate.findOne(q, SpotOrder::class.java)
        }
    }

    suspend fun get(ex: ExchangeName? = null, symbol: Symbol? = null, start: Date? = null, end: Date? = null, state: OrderStateEnum? = null): List<SpotOrder> {
        val q = Query()
        ex?.let { q.addCriteria(Criteria.where("exchange").`is`(ex)) }
        symbol?.let { q.addCriteria(Criteria.where("symbol").`is`(symbol.name())) }
        start?.let { q.addCriteria(Criteria.where("time").gte(start)) }
        end?.let { q.addCriteria(Criteria.where("time").lte(end)) }
        state?.let { q.addCriteria(Criteria.where("state").`is`(state.name)) }
        return awaitBlocking {
            mongoTemplate.find(q, SpotOrder::class.java)
        }
    }

    suspend fun getByExchangeAndExOrderId(ex: ExchangeName, exOid: String): SpotOrder? {
        val q = Query()
        q.addCriteria(Criteria.where("exOid").`is`(exOid))
                .addCriteria(Criteria.where("exchange").`is`(ex.name))
        return awaitBlocking {
            mongoTemplate.findOne(q, SpotOrder::class.java)
        }
    }

    suspend fun getByOrderId(oid: String): SpotOrder? {
        val q = Query()
        q.addCriteria(Criteria.where("oid").`is`(oid))
        return awaitBlocking {
            mongoTemplate.findOne(q, SpotOrder::class.java)
        }
    }

    suspend fun updateByExOid(o: SpotOrder) {
        val q = Query()
        q.addCriteria(Criteria.where("exOid").`is`(o.exOid))
                .addCriteria(Criteria.where("exchange").`is`(o.exchange.name))
        val d = Document()
        mongoTemplate.converter.write(o, d)
        val u = Update.fromDocument(d)
        awaitBlocking {
            mongoTemplate.updateFirst(q, u, SpotOrder::class.java)
        }
    }

    suspend fun insert(o: SpotOrder): SpotOrder {
        return awaitBlocking {
            mongoTemplate.save(o)
        }
    }

    suspend fun upsertByExoid(o: SpotOrder) {
        val q = Query()
        q.addCriteria(Criteria.where("exOid").`is`(o.exOid))
                .addCriteria(Criteria.where("exchange").`is`(o.exchange.name))
        val d = Document()
        mongoTemplate.converter.write(o, d)
        val u = Update.fromDocument(d)
        awaitBlocking {
            mongoTemplate.upsert(q, u, SpotOrder::class.java)
        }
    }

}