package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.entity.data.SpotBalance
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.Transaction
import io.vertx.core.Vertx
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@Component
class AbstractMongoRepository : CoroutineScope by CoroutineScope(Job()) {


    @Autowired
    lateinit var vertx: Vertx

    suspend fun <T> await(block: () -> T): T {
        return coroutineScope { withContext(Dispatchers.Default) { block() } }
    }

}

@Repository
interface SpotBalanceRepository : MongoRepository<SpotBalance, Long> {

}

@Repository
interface SpotOrderRepository : MongoRepository<SpotOrder, Long> {

}

@Repository
interface TransactionRepository : MongoRepository<Transaction, Long> {

}