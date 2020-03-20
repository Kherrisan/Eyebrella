package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.ServiceDataAdaptor

abstract class Exchange(
    val name: ExchangeName,
    val spot: Spot,
    val adaptor: ServiceDataAdaptor
)