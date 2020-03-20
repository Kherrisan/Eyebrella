package cn.kherrisan.eyebrella.arbitrage

import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

const val ARBITRAGE_CYCLES_NODE_LIMIT = 3

enum class ArbitrageState {
    IDLE,
    BUILDING,
    RUNNING
}

object Arbitrage : CoroutineScope by CoroutineScope(newSingleThreadContext("arbitrage-context")) {

    var state: ArbitrageState = ArbitrageState.IDLE
    private val symbolMap: MutableMap<Symbol, ArbitrageSymbolNode> = ConcurrentHashMap()
    private val cycles: MutableList<ArbitrageCycle> = mutableListOf()

    suspend fun maxN(
        min: BigDecimal = BigDecimal.ZERO,
        max: BigDecimal = BigDecimal.TEN,
        n: Int = 10
    ): List<ArbitrageCycle> {
        //冒泡排序，找前 n 个
        val list = mutableListOf<ArbitrageCycle>()
        var lastMax: ArbitrageCycle? = null
        for (t in 0..n) {
            var cmax: ArbitrageCycle? = null
            for (cycle in cycles) {
                if (cycle.tail?.state == PathNodeState.WORKING
                    && cycle.feeAdjustedSyntheticPrice < max
                    && cycle.feeAdjustedSyntheticPrice > min
                    && (cmax == null || cycle.feeAdjustedSyntheticPrice > cmax.feeAdjustedSyntheticPrice)
                    && (lastMax == null || cycle.feeAdjustedSyntheticPrice < lastMax.feeAdjustedSyntheticPrice)
                ) {
                    cmax = cycle
                }
            }
            lastMax = cmax
            cmax?.let { list.add(cmax) }
        }
        return list
    }

    /**
     * 找到 synthetic 最大的 price 对应的 cycle
     */
    suspend fun max(
        min: BigDecimal = BigDecimal.ZERO,
        max: BigDecimal = BigDecimal.TEN
    ): ArbitrageCycle? {
        var res: ArbitrageCycle? = null
        for (cycle in cycles) {
            if (cycle.tail?.state == PathNodeState.WORKING
                && (res == null || cycle.feeAdjustedSyntheticPrice > res.feeAdjustedSyntheticPrice)
                && cycle.feeAdjustedSyntheticPrice > min
                && cycle.feeAdjustedSyntheticPrice < max
            ) {
                res = cycle
            }
        }
        return res
    }

    suspend fun updateBBO(exchange: ExchangeName, bbo: BBO) {
        launch {
            if (state != ArbitrageState.RUNNING) {
                return@launch
            }
            symbolMap[bbo.symbol]?.updateBBO(exchange, bbo)
        }
    }

    fun build(symbols: List<Symbol>) {
        state = ArbitrageState.BUILDING
        symbols.forEach { symbolMap[it] = ArbitrageSymbolNode(it) }
        buildCycles(symbols, mutableSetOf(), mutableListOf(), USDT)
        state = ArbitrageState.RUNNING
    }

    private var c = 0;

    private fun buildCycles(
        symbols: List<Symbol>,
        usedSet: MutableSet<Symbol>,
        output: MutableList<Symbol>,
        asset: Currency
    ) {
        if (output.size > ARBITRAGE_CYCLES_NODE_LIMIT) {
            return
        }
        if (output.size > 2) {
            if (asset == USDT) {
                cycles.add(ArbitrageCycle.build(output, symbolMap))
                return
            }
        }
        for (symbol in symbols) {
            if (symbol !in usedSet) {
                if (symbol.base == asset) {
                    usedSet.add(symbol)
                    output.add(symbol)
                    buildCycles(symbols, usedSet, output, symbol.quote)
                    output.removeAt(output.size - 1)
                    usedSet.remove(symbol)
                } else if (symbol.quote == asset) {
                    usedSet.add(symbol)
                    output.add(symbol)
                    buildCycles(symbols, usedSet, output, symbol.base)
                    output.removeAt(output.size - 1)
                    usedSet.remove(symbol)
                }
            }
        }
    }

}