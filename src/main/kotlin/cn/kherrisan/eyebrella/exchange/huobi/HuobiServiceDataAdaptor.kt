package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.AbstractServiceDataAdaptor
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.enumeration.*
import cn.kherrisan.eyebrella.entity.Symbol
import com.google.gson.JsonElement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

@Component
open class HuobiServiceDataAdaptor @Autowired constructor(metaInfo: HuobiMetaInfo)
    : AbstractServiceDataAdaptor(metaInfo) {

    override val klinePeriodMap: Map<KlinePeriodEnum, String> =
            mapOf(
                    KlinePeriodEnum._1MIN to "1min",
                    KlinePeriodEnum._5MIN to "5min",
                    KlinePeriodEnum._15MIN to "15min",
                    KlinePeriodEnum._30MIN to "30min",
                    KlinePeriodEnum._60MIN to "60min",
                    KlinePeriodEnum._4HOUR to "4hour",
                    KlinePeriodEnum._1DAY to "1day",
                    KlinePeriodEnum._1MON to "1mon",
                    KlinePeriodEnum._1WEEK to "1week",
                    KlinePeriodEnum._1YEAR to "1year"
            )

    override val orderStateMap: Map<String, OrderStateEnum> = mapOf(
            "created" to OrderStateEnum.CREATED,
            "submitted" to OrderStateEnum.SUBMITTED,
            "partial-filled" to OrderStateEnum.PARTIAL_FILLED,
            "filled" to OrderStateEnum.FILLED,
            "canceled" to OrderStateEnum.CANCELED,
            "partial-canceled" to OrderStateEnum.CANCELED
    )

    override val loanStateMap: Map<LoanStatusEnum, String> = mapOf(
            LoanStatusEnum.CREATED to "created",
            LoanStatusEnum.ACCRUAL to "accrual",
            LoanStatusEnum.REPAYED to "cleared",
            LoanStatusEnum.INVALID to "invalid"
    )

    override fun string(symbol: Symbol): String = "${symbol.base}${symbol.quote}".toLowerCase()

    /**
     * 使用新加坡时间(和北京时间处于同一个时区)，以ms为单位的时间戳。
     *
     * @param date Date
     * @return String
     */
    override fun string(date: Date): String {
        return date.time.toString()
    }

    /**
     * 使用新加坡时间(和北京时间处于同一个时区)，以ms为单位的时间戳。况且时间戳本身就不需要做额外处理。
     *
     * @param str String
     * @return Date
     */
    override fun date(str: String): Date {
        return MyDate(str.toLong())
    }

    override fun date(e: JsonElement): Date {
        return MyDate(e.asLong)
    }

    /**
     * 使用不带分隔符的base、quote字符串来表示symbol
     *
     * @param str String
     * @return Symbol
     */
    override fun symbol(str: String): Symbol {
        return Symbol.parse(str, metaInfo.currencyList)
    }

    /**
     * huobi api中可能出现两种形式的表示side的字符串：limit-buy和buy
     *
     * @param str String
     * @return TradeDirectionEnum
     */
    override fun orderSide(str: String): OrderSideEnum {
        return if (str.contains("-")) {
            val i = str.indexOf("-")
            OrderSideEnum.valueOf(str.substring(0, i).toUpperCase())
        } else {
            OrderSideEnum.valueOf(str.toUpperCase())
        }
    }

    override fun orderType(str: String): OrderTypeEnum {
        return if (str.contains("-")) {
            // buy-limit
            val i = str.indexOf("-")
            OrderTypeEnum.valueOf(str.substring(i + 1).toUpperCase())
        } else {
            // limit or market
            OrderTypeEnum.valueOf(str.toUpperCase())
        }
    }

    override fun string(state: OrderStateEnum): String {
        return if (state == OrderStateEnum.CANCELED) {
            "canceled"
        } else {
            orderStateMap.entries.find { it.value == state }!!.key
        }
    }
}