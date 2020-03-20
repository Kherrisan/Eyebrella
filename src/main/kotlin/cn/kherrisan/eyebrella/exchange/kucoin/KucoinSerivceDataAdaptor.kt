package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.AbstractServiceDataAdaptor
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.common.UNSUPPORTED_KLINE_PERIOD_ENUM
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class KucoinSerivceDataAdaptor @Autowired constructor(metaInfo: KucoinMetaInfo) : AbstractServiceDataAdaptor(metaInfo) {

    override val klinePeriodMap: Map<KlinePeriodEnum, String> = mapOf(
            KlinePeriodEnum._1MIN to "1min",
            KlinePeriodEnum._3MIN to "3min",
            KlinePeriodEnum._5MIN to "5min",
            KlinePeriodEnum._15MIN to "15min",
            KlinePeriodEnum._30MIN to "30min",
            KlinePeriodEnum._60MIN to "1hour",
            KlinePeriodEnum._2HOUR to "2hour",
            KlinePeriodEnum._4HOUR to "4hour",
            KlinePeriodEnum._6HOUR to "6hour",
            KlinePeriodEnum._8HOUR to "8hour",
            KlinePeriodEnum._12HOUR to "12hour",
            KlinePeriodEnum._1DAY to "1day",
            KlinePeriodEnum._1WEEK to "1week"
    )

    override fun string(symbol: Symbol): String {
        return "${symbol.base}-${symbol.quote}".toUpperCase()
    }

    override fun string(periodEnum: KlinePeriodEnum): String {
        return klinePeriodMap[periodEnum] ?: error(UNSUPPORTED_KLINE_PERIOD_ENUM(periodEnum))
    }

    override fun string(date: Date): String {
        throw NotImplementedError()
    }

    override fun string(state: OrderStateEnum): String {
        throw NotImplementedError()
    }

    override fun symbol(str: String): Symbol {
        throw NotImplementedError()
    }

    override fun date(str: String): Date {
        return MyDate(str.toLong())
    }

    override fun date(e: JsonElement): Date {
        return MyDate(e.asLong)
    }

    override fun bigDecimal(e: JsonElement): BigDecimal {
        return e.asString.toBigDecimal()
    }

    override fun orderState(str: String): OrderStateEnum {
        throw NotImplementedError()
    }
}