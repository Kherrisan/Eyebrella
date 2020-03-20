package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open
import java.math.BigDecimal

/**
 * 市场交易深度
 *
 * @property price BigDecimal 价格
 * @property amount BigDecimal 待成交币种个数（基础货币）
 * @constructor
 */
@Open
data class DepthItem(var price: BigDecimal, var amount: BigDecimal) : Comparable<DepthItem> {
    override fun compareTo(other: DepthItem): Int {
        return price.compareTo(other.price)
    }
}