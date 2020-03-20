package cn.kherrisan.eyebrella.entity.data

import cn.kherrisan.eyebrella.arbitrage.ArbitrageCycleNode
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.entity.BBO
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Symbol
import io.vertx.core.Promise
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.util.*

@Document("spot_depth")
class ExchangeDepthItem(
    @Id @AutoIncrement val did: Long,
    @Indexed val exchange: ExchangeName,
    @Indexed val symbol: Symbol,
    val ask: Boolean,
    @Decimal128 val price: BigDecimal,
    @Decimal128 var amount: BigDecimal,
    val time: Date
)

@Document("spot_balance")
data class SpotBalance @PersistenceConstructor constructor(
    @AutoIncrement @Id val bid: Long,
    @Indexed val exchange: ExchangeName,
    @Indexed val currency: Currency,
    @Decimal128 var free: BigDecimal,
    @Decimal128 var frozen: BigDecimal,
    val time: Date
) {
    constructor(exchange: ExchangeName, currency: Currency, free: BigDecimal, frozen: BigDecimal, time: Date = MyDate())
            : this(0, exchange, currency, free, frozen, time)
}

/**
 *
 * @property oid Long
 * @property exchange ExchangeName
 * @property exOid String
 * @property symbol Symbol
 * @property time Date
 * @property amount BigDecimal
 * @property price BigDecimal
 * @property side OrderSideEnum
 * @property type OrderTypeEnum
 * @property state OrderStateEnum
 * @property eyebrella Boolean
 * @constructor
 */
@Document("spot_order")
data class SpotOrder @PersistenceConstructor constructor(
    @AutoIncrement @Id val oid: Long,
    @Indexed val exchange: ExchangeName,
    val exOid: String,
    @Indexed val symbol: Symbol,
    val time: Date,
    @Decimal128 val amount: BigDecimal,
    @Decimal128 val price: BigDecimal,
    @Indexed val side: OrderSideEnum,
    val type: OrderTypeEnum,
    var state: OrderStateEnum,
    var eyebrella: Boolean
) {
    constructor(
        exchange: ExchangeName,
        exId: String,
        symbol: Symbol,
        createTime: Date,
        amount: BigDecimal,
        price: BigDecimal,
        side: OrderSideEnum,
        type: OrderTypeEnum,
        state: OrderStateEnum,
        eyebrella: Boolean = false
    )
            : this(0, exchange, exId, symbol, createTime, amount, price, side, type, state, eyebrella)
}

@Document("transaction")
data class Transaction @PersistenceConstructor constructor(
    @AutoIncrement @Id val tid: Long,
    val exchange: ExchangeName,
    val type: TransactionType,
    @Decimal128 val amount: BigDecimal,
    val currency: Currency,
    val exId: String
) {
    constructor(exchange: ExchangeName, type: TransactionType, amount: BigDecimal, currency: Currency, exId: String)
            : this(0, exchange, type, amount, currency, exId)

}

@Document("spot_fee")
data class SpotTradingFee @PersistenceConstructor constructor(
    @AutoIncrement @Id val fid: Long,
    @Indexed val exchange: ExchangeName,
    @Indexed val symbol: Symbol,
    @Decimal128 val makerFee: BigDecimal,
    @Decimal128 val takerFee: BigDecimal,
    val time: Date
) {

    constructor(
        exchange: ExchangeName,
        symbol: Symbol,
        makerFee: BigDecimal,
        takerFee: BigDecimal,
        time: Date = MyDate()
    )
            : this(0L, exchange, symbol, takerFee, makerFee, time)

}

@Document("spot_arbitrage_cycle")
data class ArbitrageCycleDocument(
    @Id @AutoIncrement val aid: Long,
    val path: List<ArbitrageCycleNode>,
    val bbos: List<BBO>,
    val time: Date = MyDate()
) {

    override fun toString(): String {
        return "ArbitrageCycleDocument#$aid"
    }
}