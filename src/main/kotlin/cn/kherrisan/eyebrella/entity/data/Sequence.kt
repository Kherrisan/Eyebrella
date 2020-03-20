package cn.kherrisan.eyebrella.entity.data

import org.springframework.data.mongodb.core.mapping.Document

@Document("sequence")
data class Sequence(
    val seq: Long,
    val name: String
)