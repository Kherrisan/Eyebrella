package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import com.google.gson.internal.bind.util.ISO8601Utils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.ParsePosition
import java.util.*

@Component
class OkexServiceDataAdaptor @Autowired constructor(metaInfo: OkexMetaInfo)
    : AbstractServiceDataAdaptor(metaInfo) {

    override val orderStateMap: Map<String, OrderStateEnum> = mapOf(
            "-2" to OrderStateEnum.FAILED,
            "-1" to OrderStateEnum.CANCELED,
            "0" to OrderStateEnum.SUBMITTED,
            "1" to OrderStateEnum.PARTIAL_FILLED,
            "2" to OrderStateEnum.FILLED,
            "3" to OrderStateEnum.CREATED,
            "4" to OrderStateEnum.CANCELED //4实际上对应的是撤单中
    )

    override val klinePeriodMap: Map<KlinePeriodEnum, String> = mapOf(
            KlinePeriodEnum._1MIN to "60",
            KlinePeriodEnum._3MIN to "180",
            KlinePeriodEnum._5MIN to "300",
            KlinePeriodEnum._15MIN to "900",
            KlinePeriodEnum._30MIN to "1800",
            KlinePeriodEnum._60MIN to "3600",
            KlinePeriodEnum._2HOUR to "7200",
            KlinePeriodEnum._4HOUR to "14400",
            KlinePeriodEnum._6HOUR to "21600",
            KlinePeriodEnum._12HOUR to "43200",
            KlinePeriodEnum._1DAY to "86400",
            KlinePeriodEnum._1WEEK to "604800"
    )

    override val loanStateMap: Map<LoanStatusEnum, String> = mapOf(
            LoanStatusEnum.ACCRUAL to "0",  //未还清
            LoanStatusEnum.REPAYED to "1"   //已还清
    )

    override fun string(symbol: Symbol): String {
        return "${symbol.base}-${symbol.quote}".toUpperCase()
    }

    override fun string(periodEnum: KlinePeriodEnum): String {
        return klinePeriodMap[periodEnum] ?: error(UNSUPPORTED_KLINE_PERIOD_ENUM(periodEnum))
    }

    override fun string(date: Date): String {
        return ISO8601Utils.format(date)
    }

    override fun symbol(str: String): Symbol {
        val mid = str.indexOf("-")
        return Symbol(str.substring(0, mid).toLowerCase(), str.substring(mid + 1).toLowerCase())
    }

    override fun date(str: String): Date {
        return MyDate(ISO8601Utils.parse(str, ParsePosition(0)).time)
    }

    override fun orderState(str: String): OrderStateEnum {
        return orderStateMap[str] ?: error(UNKNOWN_ORDER_STATE(str))
    }

    override fun string(state: OrderStateEnum): String {
        return orderStateMap.entries.find { it.value == state }!!.key
    }

    override fun bigDecimal(e: JsonElement): BigDecimal {
        return e.asString.toBigDecimal()
    }
}