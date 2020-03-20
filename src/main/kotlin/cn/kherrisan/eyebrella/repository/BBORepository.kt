package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.entity.BBO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class BBORepository {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    suspend fun save(bbo: BBO) {
        mongoTemplate.save(bbo)
    }
}