package cn.kherrisan.eyebrella

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.entity.BTC_USDT
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import cn.kherrisan.eyebrella.exchange.huobi.HuobiSpot
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.delay
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

fun main() {
//    Eyebrella.init()
//    SpringContainer[HuobiSpot::class]
    Eyebrella.init()
    val q = Query()
    q.addCriteria(Criteria.where("symbol").`is`(BTC_USDT))
        .addCriteria(Criteria.where("exchange").`is`(ExchangeName.HUOBI))
        .with(Sort.by(Sort.Direction.DESC, "time"))
        .limit(1)
    val l = SpringContainer[MongoTemplate::class].findOne(q, SpotTradingFee::class.java)
    println(l)
}

class TTVertx : CoroutineVerticle() {
    override suspend fun start() {
        delay(2000)
        println("dlafjsdfka")
        delay(2000)
    }
}