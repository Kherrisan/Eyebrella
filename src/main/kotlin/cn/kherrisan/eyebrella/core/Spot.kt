package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import java.math.BigDecimal

interface Spot {

    fun symbols(): List<Symbol>

    fun currencys(): List<Currency>

    fun metaInfo(symbol: Symbol): SymbolMetaInfo

    suspend fun depth(symbol: Symbol): Depth

    fun bbo(symbol: Symbol): BBO

    fun bestBid(symbol: Symbol): DepthItem

    fun bestAsk(symbol: Symbol): DepthItem

    fun openOrders(): List<SpotOrder>

    suspend fun freeBalance(currency: Currency): BigDecimal?

    suspend fun fee(symbol: Symbol): SpotTradingFee

    suspend fun orderInfo(oid: String): SpotOrder

    suspend fun dealInfo(): List<SpotOrderDeal>

    suspend fun buy(symbol: Symbol, amount: BigDecimal, price: BigDecimal? = null): TransactionResult

    suspend fun sell(symbol: Symbol, amount: BigDecimal, price: BigDecimal? = null): TransactionResult

    suspend fun cancel(order: SpotOrder): TransactionResult

    fun halfRTT(): Long?
}