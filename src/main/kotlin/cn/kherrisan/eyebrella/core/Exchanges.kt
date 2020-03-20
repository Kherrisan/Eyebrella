package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.exchange.huobi.Huobi
import cn.kherrisan.eyebrella.exchange.okex.Okex

object Exchanges {

    private val map = mutableMapOf<ExchangeName, Exchange>()

    init {
//        runBlocking {
//            ParallelLauncher()
//                .launchJob { SpringContainer[Huobi::class] }
//                .launchJob { SpringContainer[Okex::class] }
//                .await()
//        }
        map[ExchangeName.HUOBI] = SpringContainer[Huobi::class]
        map[ExchangeName.OKEX] = SpringContainer[Okex::class]
    }

    operator fun get(name: ExchangeName): Exchange = map[name]!!

    fun names(): List<ExchangeName> {
        return map.keys.toList()
    }

    fun has(exchangeName: ExchangeName): Boolean {
        return exchangeName in map
    }

    operator fun iterator(): Iterator<Exchange> {
        return map.values.iterator()
    }

}