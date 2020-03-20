package cn.kherrisan.eyebrella.repository

import cn.kherrisan.eyebrella.entity.data.ArbitrageCycleDocument
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class ArbitrageCycleDocumentRepository {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    fun save(document: ArbitrageCycleDocument): ArbitrageCycleDocument {
        return mongoTemplate.save(document)
    }
}