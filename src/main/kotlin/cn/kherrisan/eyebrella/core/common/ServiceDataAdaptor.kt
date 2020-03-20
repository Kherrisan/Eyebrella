package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.core.enumeration.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Depth
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.util.*

interface ServiceDataAdaptor : DecimalAdaptor {

    fun string(symbol: Symbol): String

    fun string(periodEnum: KlinePeriodEnum): String

    fun string(tradeRoleEnum: TradeRoleEnum): String

    fun string(date: Date): String

    fun string(state: OrderStateEnum): String

    fun string(side: OrderSideEnum): String

    fun string(type: OrderTypeEnum): String

    fun string(currency: Currency): String

    fun string(status: LoanStatusEnum): String

    fun tradeRole(e: JsonElement): TradeRoleEnum

    fun orderSide(str: String): OrderSideEnum

    fun orderSide(e: JsonElement): OrderSideEnum

    fun symbol(str: String): Symbol

    fun currency(str: String): Currency

    fun currency(e: JsonElement): Currency

    fun symbol(e: JsonElement): Symbol

    fun date(str: String): Date

    fun date(e: JsonElement): Date

    fun orderType(str: String): OrderTypeEnum

    fun orderType(e: JsonElement): OrderTypeEnum

    fun orderState(str: String): OrderStateEnum

    fun orderState(e: JsonElement): OrderStateEnum

    fun loanState(str: String): LoanStatusEnum

    fun bigDecimal(e: JsonElement, i: Int): BigDecimal

    fun bigDecimal(str: String, i: Int): BigDecimal

    fun bigDecimal(e: JsonElement): BigDecimal

    fun depth(symbol: Symbol, obj: JsonObject): Depth

    fun price(d: BigDecimal, sym: Symbol): BigDecimal

    fun price(e: JsonElement, sym: Symbol): BigDecimal

    fun size(e: JsonElement, c: Currency): BigDecimal

    fun size(d: BigDecimal, c: Currency): BigDecimal

    fun size(e: JsonElement, sym: Symbol): BigDecimal

    fun size(d: BigDecimal, sym: Symbol): BigDecimal

    fun volume(e: JsonElement, sym: Symbol): BigDecimal

    fun volume(d: BigDecimal, sym: Symbol): BigDecimal
}