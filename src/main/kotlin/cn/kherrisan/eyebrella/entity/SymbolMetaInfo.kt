package cn.kherrisan.eyebrella.entity

import java.math.BigDecimal

/**
 * 交易对的交易规范信息
 *
 * @property symbol Symbol 交易对
 * @property minSize BigDecimal 最小交易数量
 * @property sizeIncrement Int 交易数量精度
 * @property priceIncrement Int 价格的精度
 * @property volumeIncrement Int 金额的精度
 * @constructor
 */
data class SymbolMetaInfo(val symbol: Symbol,
                          val minSize: BigDecimal,
                          val sizeIncrement: Int,
                          val priceIncrement: Int,
                          val volumeIncrement: Int)