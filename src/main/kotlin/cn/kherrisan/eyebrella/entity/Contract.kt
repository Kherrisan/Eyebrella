package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.MyDate
import java.math.BigDecimal
import java.util.*

//永续合约的标志
val SWAP = MyDate()

/**
 * 合约信息
 *
 * [关于正向合约和反向合约](https://bhex.zendesk.com/hc/zh-cn/articles/360040565753-%E4%B8%89%E5%88%86%E9%92%9F%E8%AF%BB%E6%87%82%E6%AD%A3%E5%90%91%E5%90%88%E7%BA%A6%E5%92%8C%E5%8F%8D%E5%90%91%E5%90%88%E7%BA%A6)
 *
 * @property cid String 合约 id，一般该 id 会包含标的物、交割日期等信息。
 * @property symbol Symbol 指数
 * @property settlement Currency 盈亏结算和保证金币种，正向合约（USDT 本位）时，结算币种为 USDT，反向合约时，结算币种为标的物。
 * @property createDate Date 上线日期
 * @property deliveryDate Date 交割日期
 * @property size BigDecimal 合约面值
 * @property sizeCurrency Currency 合约面值计价币种，正向合约时，计价币种为标的物，反向合约时，计价币种一般是 USD。
 * @property priceIncrement Int 价格步长
 * @property sizeIncrement Int 数量步长
 * @property time Date
 * @constructor
 */
data class Contract(
        val cid: String,
        val symbol: Symbol,
        val settlement: Currency,
        val createDate: Date,
        val deliveryDate: Date,
        val size: BigDecimal,
        val sizeCurrency: Currency,
        val priceIncrement: Int,
        val sizeIncrement: Int,
        val time: Date
) {
    override fun toString(): String = cid

    fun isSwap(): Boolean = createDate == SWAP
}