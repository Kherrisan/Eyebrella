package cn.kherrisan.eyebrella.entity

import java.math.BigDecimal
import java.util.*

class SequentialDepth(symbol: Symbol,
                      time: Date,
                      asks: MutableList<DepthItem>,
                      bids: MutableList<DepthItem>,
                      var seq: Long,
                      var prev: Long)
    : Depth(symbol, time, asks, bids) {

    constructor(symbol: Symbol, time: Date, askMap: MutableMap<BigDecimal, BigDecimal>, bidMap: MutableMap<BigDecimal, BigDecimal>, seq: Long, prev: Long)
            : this(symbol, time, mutableListOf<DepthItem>(), mutableListOf<DepthItem>(), seq, prev) {
        askMap.entries.forEach { asks.add(DepthItem(it.key, it.value)) }
        bidMap.entries.forEach { bids.add(DepthItem(it.key, it.value)) }
        asks.sortDescending() //asks降序排列
        bids.sortDescending() //bids降序排列
    }

    constructor(depth: Depth, seq: Long, prev: Long)
            : this(depth.symbol, depth.time, depth.asks, depth.bids, seq, prev)

    override fun toString(): String {
        return "SequentialDepth(symbol=$symbol, time=$time, seq=$seq, prev=$prev)"
    }
}