package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open
import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import java.math.BigDecimal
import java.util.*

/**
 * 借贷订单
 *
 * @property oid String 订单 id
 * @property symbol Symbol 交易对（仓）
 * @property currency Currency 借贷币种
 * @property amount BigDecimal 数量
 * @property paidAmount BigDecimal 已偿还数量
 * @property time Date 订单创建时间
 * @property interestRate BigDecimal 利率
 * @property interest BigDecimal 利息总额
 * @property paidInterest BigDecimal 已偿还利息
 * @property accruedTime Date 最近一次计息时间
 * @property status LoanStatusEnum 订单状态
 * @constructor
 */
@Open
data class LoanOrder(
        val oid: String,
        var symbol: Symbol,
        var currency: Currency,
        var amount: BigDecimal,
        var paidAmount: BigDecimal,
        var time: Date,
        var interestRate: BigDecimal,
        var interest: BigDecimal,
        var paidInterest: BigDecimal,
        var accruedTime: Date,
        var status: LoanStatusEnum
)