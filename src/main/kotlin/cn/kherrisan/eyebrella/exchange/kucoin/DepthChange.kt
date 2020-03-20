package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import java.math.BigDecimal

data class DepthChange(val price: BigDecimal, val size: BigDecimal, val seq: Long, val side: OrderSideEnum)