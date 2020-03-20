package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.core.enumeration.KlinePeriodEnum

fun UNSUPPORTED_KLINE_PERIOD_ENUM(enum: KlinePeriodEnum): String = "Unsupported kline enum $enum"

fun UNKNOWN_ORDER_TYPE(str: String): String = "Unknown order type for: $str"

fun UNKNOWN_ORDER_STATE(str: String): String = "Unknown order state for $str"

