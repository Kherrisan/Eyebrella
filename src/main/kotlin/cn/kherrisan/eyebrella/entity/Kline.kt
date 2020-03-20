package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.Open
import cn.kherrisan.eyebrella.entity.data.AutoIncrement
import cn.kherrisan.eyebrella.entity.data.Decimal128
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import java.math.BigDecimal
import java.util.*

/**
 *
 * @property symbol Symbol
 * @property time Date
 * @property open BigDecimal
 * @property close BigDecimal
 * @property high BigDecimal
 * @property low BigDecimal
 * @property volume BigDecimal
 * @constructor
 */
@Open
data class Kline(
    var symbol: Symbol,
    var time: Date,
    var open: BigDecimal,
    var close: BigDecimal,
    var high: BigDecimal,
    var low: BigDecimal,
    var volume: BigDecimal
)