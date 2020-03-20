package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.entity.ArbitrageCycleLink
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@Component
class ArbitrageCycleLinkRepository {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    fun save(link: ArbitrageCycleLink) {
        mongoTemplate.save(link)
    }

    fun getById(aid: Long): ArbitrageCycleLink? {
        return mongoTemplate.findById(aid, ArbitrageCycleLink::class.java)
    }
}