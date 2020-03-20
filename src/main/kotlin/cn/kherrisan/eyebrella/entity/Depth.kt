package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.SINGLE_THREAD_CONTEXT
import cn.kherrisan.eyebrella.core.common.Open
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.*

/**
 * 交易深度，就是每个价格有多少金额的挂单的累计结果
 *
 * @property symbol Symbol 交易对
 * @property time Long 时间
 * @property asks 卖单深度，在列表中按照price由高到低的顺序排列
 * @property bids 买单深度，在列表中按照price由高到低的顺序排列，这样asks-bids的价格和深度正好组成一个沙漏形
 * @constructor
 */
@Open
data class Depth(
    var symbol: Symbol,
    var time: Date,
    var asks: MutableList<DepthItem>,
    var bids: MutableList<DepthItem>
) {
    constructor(
        symbol: Symbol,
        time: Date,
        askMap: MutableMap<BigDecimal, BigDecimal>,
        bidMap: MutableMap<BigDecimal, BigDecimal>
    )
            : this(symbol, time, mutableListOf<DepthItem>(), mutableListOf<DepthItem>()) {
        askMap.entries.forEach { asks.add(DepthItem(it.key, it.value)) }
        bidMap.entries.forEach { bids.add(DepthItem(it.key, it.value)) }
        asks.sortDescending() //asks降序排列
        bids.sortDescending() //bids降序排列
    }

    fun minAsk(): DepthItem = asks.last()

    fun maxBid(): DepthItem = bids.first()

    fun copy(): Depth {
        val cp = Depth(symbol, time, mutableListOf(), mutableListOf())
        asks.forEach { cp.asks.add(it) }
        bids.forEach { cp.bids.add(it) }
        return cp
    }

    fun merge(change: Depth) {
        runBlocking {
            withContext(SINGLE_THREAD_CONTEXT) {
                mergeDepthList(asks, change.asks)
                mergeDepthList(bids, change.bids)
            }
        }
    }

    private fun mergeDepthList(baseList: MutableList<DepthItem>, changeList: MutableList<DepthItem>) {
        var cursor = 0
        val baseSize = baseList.size
        for (ch in changeList) {
            while (cursor < baseSize && baseList[cursor].price > ch.price) {
                cursor++
            }
            if (cursor == baseSize) {
                baseList.add(ch)
                continue
            }
            if (baseList[cursor].price.compareTo(ch.price) == 0) {
                baseList[cursor++] = ch
            } else {
                baseList.add(ch)
            }
        }
        val itr = baseList.iterator()
        while (itr.hasNext()) {
            if (itr.next().amount.compareTo(BigDecimal.ZERO) == 0) {
                itr.remove()
            }
        }
        baseList.sortDescending()
    }
}