package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.entity.BBO
import cn.kherrisan.eyebrella.entity.SpotOrderDeal
import cn.kherrisan.eyebrella.entity.data.SpotBalance

interface MarketDataListener {
    fun updateBBO(bbo: BBO)
}

interface TradingDataListener {
    fun updateBalance(newBalance: SpotBalance)
    fun updateOrderDeal(newOrderDeal: SpotOrderDeal)
}