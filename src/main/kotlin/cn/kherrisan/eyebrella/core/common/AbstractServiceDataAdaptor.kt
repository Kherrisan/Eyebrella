package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.core.enumeration.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Depth
import cn.kherrisan.eyebrella.entity.DepthItem
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

abstract class AbstractServiceDataAdaptor(val metaInfo: ExchangeMetaInfo)
    : ServiceDataAdaptor {

    open val klinePeriodMap: Map<KlinePeriodEnum, String> = mapOf()
    open val orderStateMap: Map<String, OrderStateEnum> = mapOf()
    open val loanStateMap: Map<LoanStatusEnum, String> = mapOf()

    override fun string(tradeRoleEnum: TradeRoleEnum): String {
        return tradeRoleEnum.name
    }

    override fun string(currency: Currency): String {
        return currency.name.toLowerCase()
    }

    override fun string(side: OrderSideEnum): String {
        return side.toString()
    }

    override fun string(type: OrderTypeEnum): String {
        return type.toString()
    }

    override fun string(periodEnum: KlinePeriodEnum): String {
        return klinePeriodMap[periodEnum] ?: error("Unsupported kline enum $periodEnum")
    }

    override fun string(status: LoanStatusEnum): String {
        return loanStateMap[status] ?: error("Unsupported loan status enum $status")
    }

    override fun loanState(str: String): LoanStatusEnum {
        return loanStateMap.entries.find { it.value == str }?.key ?: error("Unsupported loan status enum $str")
    }

    override fun tradeRole(e: JsonElement): TradeRoleEnum {
        return TradeRoleEnum.valueOf(e.asString.toUpperCase())
    }

    override fun orderType(str: String): OrderTypeEnum {
        return OrderTypeEnum.valueOf(str.toUpperCase())
    }

    override fun orderSide(str: String): OrderSideEnum {
        return OrderSideEnum.valueOf(str.toUpperCase())
    }

    open override fun orderSide(e: JsonElement): OrderSideEnum {
        return orderSide(e.asString)
    }

    open override fun symbol(e: JsonElement): Symbol {
        return symbol(e.asString)
    }

    open override fun date(e: JsonElement): Date {
        return date(e.asString)
    }

    open override fun orderType(e: JsonElement): OrderTypeEnum {
        return orderType(e.asString)
    }

    override fun orderState(str: String): OrderStateEnum {
        return orderStateMap[str] ?: error("Unknown order type for: $str")
    }

    open override fun orderState(e: JsonElement): OrderStateEnum {
        return orderState(e.asString)
    }

    override fun bigDecimal(e: JsonElement, i: Int): BigDecimal {
        return bigDecimal(e).setScale(i, RoundingMode.DOWN)
    }

    override fun bigDecimal(str: String, i: Int): BigDecimal {
        return str.toBigDecimal().setScale(i)
    }

    open override fun bigDecimal(e: JsonElement): BigDecimal {
        try {
            return e.asBigDecimal
        } catch (exc: NumberFormatException) {
            return e.asString.toBigDecimal()
        }
    }

    override fun currency(str: String): Currency {
        return Currency(str.toLowerCase())
    }

    override fun currency(e: JsonElement): Currency {
        return currency(e.asString)
    }

    override fun depth(symbol: Symbol, obj: JsonObject): Depth {
        val asks = mutableListOf<DepthItem>()
        obj["asks"]?.asJsonArray?.map { it.asJsonArray }
                ?.forEach {
                    asks.add(
                            DepthItem(bigDecimal(it[0], priceIncrement(symbol)),
                                    bigDecimal(it[1], sizeIncrement(symbol))
                            ))
                }
        val bids = mutableListOf<DepthItem>()
        obj["bids"]?.asJsonArray?.map { it.asJsonArray }
                ?.forEach {
                    bids.add(
                            DepthItem(bigDecimal(it[0], priceIncrement(symbol)),
                                    bigDecimal(it[1], sizeIncrement(symbol))
                            ))
                }
        asks.sortDescending()
        bids.sortDescending()
        return Depth(symbol, MyDate(), asks, bids)
    }

    override fun price(d: BigDecimal, sym: Symbol): BigDecimal {
        return d.setScale(priceIncrement(sym), RoundingMode.DOWN)
    }

    override fun price(e: JsonElement, sym: Symbol): BigDecimal {
        return price(bigDecimal(e), sym)
    }

    override fun size(e: JsonElement, c: Currency): BigDecimal {
        return size(bigDecimal(e), c)
    }

    override fun size(d: BigDecimal, c: Currency): BigDecimal {
        return d.setScale(sizeIncrement(c), RoundingMode.DOWN)
    }

    override fun size(e: JsonElement, sym: Symbol): BigDecimal {
        return size(bigDecimal(e), sym)
    }

    override fun size(d: BigDecimal, sym: Symbol): BigDecimal {
        return d.setScale(sizeIncrement(sym), RoundingMode.DOWN)
    }

    override fun volume(e: JsonElement, sym: Symbol): BigDecimal {
        return volume(bigDecimal(e), sym)
    }

    override fun volume(d: BigDecimal, sym: Symbol): BigDecimal {
        return d.setScale(volumeIncrement(sym), RoundingMode.DOWN)
    }

    override fun sizeIncrement(currency: Currency?): Int {
        return metaInfo.currencyMetaInfo[currency]?.sizeIncrement ?: 4
    }

    override fun sizeIncrement(symbol: Symbol?): Int {
        return metaInfo.symbolMetaInfo[symbol]?.sizeIncrement ?: 4
    }

    override fun priceIncrement(symbol: Symbol?): Int {
        return metaInfo.symbolMetaInfo[symbol]?.priceIncrement ?: 4
    }

    override fun volumeIncrement(symbol: Symbol?): Int {
        return metaInfo.symbolMetaInfo[symbol]?.volumeIncrement ?: 4
    }
}