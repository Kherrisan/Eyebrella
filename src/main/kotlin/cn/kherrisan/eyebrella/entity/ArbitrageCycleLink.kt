package cn.kherrisan.eyebrella.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("spot_arbitrage_cycle")
data class ArbitrageCycleLink(
    @Id @Indexed val aid: Long,
    val cid: Long,
    val prev: Long = -1,
    val succ: Long = -1
)