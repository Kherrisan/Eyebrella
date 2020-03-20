package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.entity.data.AutoIncrement
import cn.kherrisan.eyebrella.entity.data.Decimal128
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.*

data class AskBid(val ask: BigDecimal, val bid: BigDecimal)

/**
 * Best bid,offer
 */
@Document("spot_bbo")
data class BBO(
    @Id @AutoIncrement val bbid: Long,
    @Indexed val symbol: Symbol,
    @Indexed val exchange: ExchangeName,
    @Decimal128 val ask: BigDecimal,
    @Decimal128 val askAmount: BigDecimal,
    @Decimal128 val bid: BigDecimal,
    @Decimal128 val bidAmount: BigDecimal,
    val time: Date
) {
    constructor(
        symbol: Symbol,
        exchange: ExchangeName,
        ask: BigDecimal,
        askAmount: BigDecimal,
        bid: BigDecimal,
        bidAmount: BigDecimal,
        time: Date
    )
            : this(0L, symbol, exchange, ask, askAmount, bid, bidAmount, time)
}