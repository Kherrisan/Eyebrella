package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.TradeRoleEnum
import java.math.BigDecimal
import java.util.*

/**
 *
 * @property did String 成交 id
 * @property oid String 订单 id
 * @property symbol Symbol 交易对
 * @property state OrderStateEnum 订单当前状态
 * @property role TradeRoleEnum taker 还是 maker
 * @property price BigDecimal 成交价格
 * @property filledAmount BigDecimal 最近成交数量（金额）
 * @property fee BigDecimal 手续费
 * @property time Date 成交时间
 * @constructor
 */
@Open
data class SpotOrderDeal(
        val did: String,
        val oid: String,
        val symbol: Symbol,
        val state: OrderStateEnum,
        val role: TradeRoleEnum,
        val price: BigDecimal,
        val filledAmount: BigDecimal,
        val fee: BigDecimal,
        val time: Date
)