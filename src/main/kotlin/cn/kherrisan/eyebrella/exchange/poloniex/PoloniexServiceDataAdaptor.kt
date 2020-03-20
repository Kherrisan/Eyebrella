package cn.kherrisan.eyebrella.exchange.poloniex

import cn.kherrisan.eyebrella.core.common.AbstractServiceDataAdaptor
import cn.kherrisan.eyebrella.core.common.DT_FORMAT
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.entity.Symbol
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

@Component
class PoloniexServiceDataAdaptor @Autowired constructor(metaInfo: PoloniexMetaInfo) : AbstractServiceDataAdaptor(metaInfo) {
    override fun string(symbol: Symbol): String {
        return "${symbol.quote}_${symbol.base}".toUpperCase()
    }

    override fun string(periodEnum: KlinePeriodEnum): String {
        return periodEnum.toSeconds().toString()
    }

    override fun string(date: Date): String {
        throw NotImplementedError()
    }

    override fun string(state: OrderStateEnum): String {
        throw NotImplementedError()
    }

    override fun orderSide(str: String): OrderSideEnum {
        return OrderSideEnum.valueOf(str.toUpperCase())
    }

    override fun symbol(str: String): Symbol {
        val mid = str.indexOf("_")
        return Symbol(str.substring(mid + 1).toLowerCase(), str.substring(0, mid).toLowerCase())
    }

    override fun date(str: String): Date {
        return MyDate(DT_FORMAT.parse(str).time)
    }

    override fun orderType(str: String): OrderTypeEnum {
        throw NotImplementedError()
    }

    override fun orderState(str: String): OrderStateEnum {
        throw NotImplementedError()
    }
}