package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.core.service.SpotMarginTradingService
import cn.kherrisan.eyebrella.core.websocket.Subscription
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.SpotOrderDeal
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.data.SpotBalance

interface SpotTradingService : SpotMarginTradingService {

    /**
     * 查询账户所有币种余额
     *
     * 会自动过滤余额过小的币种
     *
     * @return Map<Currency, Balance>
     */
    suspend fun getBalance(): Map<Currency, SpotBalance>

    /**
     * 订阅账户余额的快照
     *
     * @param symbol Symbol
     * @return Subscription<SpotBalance>
     */
    suspend fun subscribeBalance(symbol: Symbol? = null): Subscription<SpotBalance>

    /**
     * 订阅账户订单增量数据
     *
     * @param symbol Symbol
     * @return Subscription<SpotOrder>
     */
    suspend fun subscribeOrderDeal(symbol: Symbol? = null): Subscription<SpotOrderDeal>
}