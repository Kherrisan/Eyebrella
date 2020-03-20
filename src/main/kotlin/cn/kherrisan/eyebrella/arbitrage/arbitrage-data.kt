package cn.kherrisan.eyebrella.arbitrage

import cn.kherrisan.eyebrella.core.Exchanges
import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.entity.BBO
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.USDT
import cn.kherrisan.eyebrella.entity.data.ArbitrageCycleDocument
import cn.kherrisan.eyebrella.entity.data.Decimal128
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.*

val MAX_BIGDECIMAL = 100000000f.toBigDecimal()
val MIN_BIGDECIMAL = -MAX_BIGDECIMAL
val logger = LogManager.getLogger()

enum class PathNodeState {
    BLANK,
    HOLDING,
    WORKING
}

fun Symbol.price(exchange: ExchangeName, d: BigDecimal): BigDecimal {
    return Exchanges[exchange].adaptor.price(d, this)
}

data class ArbitrageCycle(
    var head: ArbitrageCycleNode? = null,
    var tail: ArbitrageCycleNode? = null
) {

    var time: Date? = null
    var md5: String? = null

    val syntheticPrice: BigDecimal
        get() = tail!!.syntheticPrice.setScale(6, RoundingMode.HALF_UP)

    val feeAdjustedSyntheticPrice: BigDecimal
        get() = tail!!.feeAdjustedSyntheticPrice.setScale(6, RoundingMode.HALF_UP)

    fun isProfitable(): Boolean = feeAdjustedSyntheticPrice.compareTo(BigDecimal.ONE) > 0

    fun document(bbos: List<BBO>): ArbitrageCycleDocument {
        val nodes = mutableListOf<ArbitrageCycleNode>()
        var itr = head
        while (itr != null) {
            nodes.add(itr)
            itr = itr.succ
        }
        return ArbitrageCycleDocument(0L, nodes, bbos)
    }

    fun md5(): String {
        if (md5 == null) {
            var string = ""
            var itr = head
            while (itr != null) {
                val direction = if (itr.isBid) "bid" else "ask"
                string = "$string->${itr.symbolNode.symbol}($direction at ${itr.bestArbitrageExchange})"
                itr = itr.succ
            }
            md5 = cn.kherrisan.eyebrella.core.common.md5(string)
        }
        return md5!!
    }

    fun fullString(): String {
        var string = ""
        var itr = head
        while (itr != null) {
            val direction = if (itr.isBid) "bid" else "ask"
            string = "$string->${itr.symbolNode.symbol}($direction ${itr.bestPrice} at ${itr.bestArbitrageExchange})"
            itr = itr.succ
        }
        return string.removePrefix("->")
    }

    override fun toString(): String {
        var asset = USDT
        if (tail?.state != PathNodeState.WORKING) {
            return "Not Working Cycle"
        }
        var string = "usdt->"
        var itr = head
        while (itr != null) {
            if (asset == itr.symbolNode.symbol.base) {
                asset = itr.symbolNode.symbol.quote
            } else {
                asset = itr.symbolNode.symbol.base
            }
            string = "$string$asset->"
            itr = itr.succ
        }
        return string.removeSuffix("->")
    }

    override fun hashCode(): Int {
        return md5.hashCode()
    }

    companion object {
        fun build(symbols: List<Symbol>, symbolMap: Map<Symbol, ArbitrageSymbolNode>): ArbitrageCycle {
            var asset = USDT
            val cycle = ArbitrageCycle()
            var lastPathNode: ArbitrageCycleNode? = null
            for (symbol in symbols) {
                var pathNode = symbolMap[symbol]!!.newPathNode(asset == symbol.quote)
                if (cycle.head == null) {
                    cycle.head = pathNode
                    pathNode.isHead = true
                }
                for (exchange in Exchanges) {
                    if (symbol in exchange.spot.symbols()) {
                        val exchangeSymbolNode = ArbitrageExchangeSymbolNode()
                        runBlocking {
                            exchangeSymbolNode.feeRate = exchange.spot.fee(symbol).takerFee
                        }
                        symbolMap[symbol]!!.arbitrageNodeMap[exchange.name] = exchangeSymbolNode
                    }
                }
                if (asset == symbol.base) {
                    asset = symbol.quote
                } else {
                    asset = symbol.base
                }
                if (lastPathNode != null) {
                    lastPathNode.succ = pathNode
                }
                pathNode.prev = lastPathNode
                lastPathNode = pathNode
            }
            cycle.tail = lastPathNode
            return cycle
        }
    }
}

class ArbitrageExchangeSymbolNode() {

    var askPrice: BigDecimal = MAX_BIGDECIMAL
    var bidPrice: BigDecimal = 0f.toBigDecimal()
    var feeRate: BigDecimal = 0f.toBigDecimal()
    var feeAdjustedAskPrice: BigDecimal = askPrice
    var feeAdjustedBidPrice: BigDecimal = bidPrice
    var floatFeeAdjustedAskPrice: BigDecimal = feeAdjustedAskPrice
    var floatFeeAdjustedBidPrice: BigDecimal = feeAdjustedBidPrice

    fun floatRate(symbol: Symbol, exchange: ExchangeName): BigDecimal {
        return 0.001f.toBigDecimal()
    }

    fun updatePrice(symbol: Symbol, exchange: ExchangeName, bbo: BBO) {
        askPrice = bbo.ask
        feeAdjustedAskPrice = askPrice.divide(BigDecimal.ONE.minus(feeRate), MathContext.DECIMAL64)
        feeAdjustedAskPrice = symbol.price(exchange, feeAdjustedAskPrice)
        floatFeeAdjustedAskPrice = feeAdjustedAskPrice.multiply(
            BigDecimal.ONE + floatRate(symbol, exchange),
            MathContext.DECIMAL64
        )
        floatFeeAdjustedAskPrice = symbol.price(exchange, floatFeeAdjustedAskPrice)
        bidPrice = bbo.bid
        feeAdjustedBidPrice = bidPrice.multiply(BigDecimal.ONE.minus(feeRate), MathContext.DECIMAL64)
        feeAdjustedBidPrice = symbol.price(exchange, feeAdjustedBidPrice)
        floatFeeAdjustedBidPrice = feeAdjustedBidPrice.multiply(
            BigDecimal.ONE - floatRate(symbol, exchange),
            MathContext.DECIMAL64
        )
        floatFeeAdjustedBidPrice = symbol.price(exchange, floatFeeAdjustedBidPrice)
    }
}

data class ArbitrageCycleNode(
    val isBid: Boolean,
    val symbolNode: ArbitrageSymbolNode,
    var isHead: Boolean = false
) {

    var state = PathNodeState.BLANK
    var bestArbitrageExchange: ExchangeName? = null

    @Decimal128
    var bestArbitrageExchangeFloatRate: BigDecimal = BigDecimal.ZERO

    @Decimal128
    var bestPrice: BigDecimal

    @Decimal128
    var bestFeeAdjustedPrice: BigDecimal

    @Decimal128
    var bestFloatFeeAdjustedPrice: BigDecimal

    @Decimal128
    var syntheticPrice: BigDecimal = BigDecimal.ONE

    @Decimal128
    var feeAdjustedSyntheticPrice: BigDecimal = BigDecimal.ONE

    @Decimal128
    var floatFeeAdjustedSyntheticPrice: BigDecimal = BigDecimal.ONE

    @org.springframework.data.annotation.Transient
    var succ: ArbitrageCycleNode? = null

    @org.springframework.data.annotation.Transient
    var prev: ArbitrageCycleNode? = null

    init {
        if (isBid) {
            //买方向，关注最低卖价，price 初始值为一个很大的数
            bestPrice = MAX_BIGDECIMAL
            bestFeeAdjustedPrice = MAX_BIGDECIMAL
            bestFloatFeeAdjustedPrice = MAX_BIGDECIMAL
        } else {
            //卖方向，关注的是最高买价，初始值设为一个很小的数
            bestPrice = BigDecimal.ZERO
            bestFeeAdjustedPrice = BigDecimal.ZERO
            bestFloatFeeAdjustedPrice = BigDecimal.ZERO
        }
    }

    fun updatePrice(exchange: ExchangeName, bbo: BBO) {
        //更新 该交易所的 price
        logger.trace("$this updatePrice with $bbo")
        val node = symbolNode.arbitrageNodeMap[exchange]
        if (node == null) {
            return
        }
        node.updatePrice(symbolNode.symbol, exchange, bbo)
        if (state == PathNodeState.BLANK) {
            state = PathNodeState.HOLDING
        }
        logger.trace("current feeAP is $bestFeeAdjustedPrice")
        if (isBid) {
            logger.trace("node FAP is ${node.feeAdjustedBidPrice}")
            logger.trace("is bid")
            if (node.feeAdjustedAskPrice < bestFeeAdjustedPrice) {
                //如果是买方向，则关注最低卖价
                //如果新价格比当前的价格还要低
                //但手续费调整后价格应该是 askPrice/(1-feeRate)
                bestArbitrageExchange = exchange
                bestPrice = node.askPrice
                bestFeeAdjustedPrice = node.feeAdjustedAskPrice
                bestFloatFeeAdjustedPrice = node.floatFeeAdjustedAskPrice
                bestArbitrageExchangeFloatRate = node.floatRate(symbolNode.symbol, exchange)
            } else {
                //新的价格可能高了，这时候需要再找一个最低价
                val exchangeOfBest = symbolNode.arbitrageNodeMap.entries.minBy { it.value.feeAdjustedAskPrice }
                bestArbitrageExchange = exchangeOfBest!!.key
                bestPrice = exchangeOfBest.value.askPrice
                bestFeeAdjustedPrice = exchangeOfBest.value.feeAdjustedAskPrice
                bestFloatFeeAdjustedPrice = exchangeOfBest.value.floatFeeAdjustedAskPrice
                bestArbitrageExchangeFloatRate =
                    exchangeOfBest.value.floatRate(symbolNode.symbol, bestArbitrageExchange!!)
            }
        } else {
            logger.trace("is ask")
            logger.trace("node FAP is ${node.feeAdjustedAskPrice}")
            //如果是卖方向，则关注最高买价
            if (node.feeAdjustedBidPrice > bestFeeAdjustedPrice) {
                bestArbitrageExchange = exchange
                bestPrice = node.bidPrice
                bestFeeAdjustedPrice = node.feeAdjustedBidPrice
                bestFloatFeeAdjustedPrice = node.floatFeeAdjustedBidPrice
                bestArbitrageExchangeFloatRate = node.floatRate(symbolNode.symbol, exchange)
            } else {
                val exchangeOfBest = symbolNode.arbitrageNodeMap.entries.maxBy { it.value.feeAdjustedBidPrice }
                bestArbitrageExchange = exchangeOfBest!!.key
                bestPrice = exchangeOfBest.value.bidPrice
                bestFeeAdjustedPrice = exchangeOfBest.value.feeAdjustedBidPrice
                bestFloatFeeAdjustedPrice = exchangeOfBest.value.floatFeeAdjustedBidPrice
                bestArbitrageExchangeFloatRate =
                    exchangeOfBest.value.floatRate(symbolNode.symbol, bestArbitrageExchange!!)
            }
        }
        logger.trace("after updatePrice BP is $bestPrice BFAP is $bestFeeAdjustedPrice")
        //更新 syntheticPrice
        updateSyntheticPrice()
    }

    /**
     * 更新该节点以及所有后续节点的合成价格
     */
    private fun updateSyntheticPrice() {
        logger.trace("updateSyntheticPrice")
        val s = symbolNode.symbol.price(ExchangeName.HUOBI, syntheticPrice).scale()
        var prevSynthetic: BigDecimal
        var prevFeeAdjustedSynthetic: BigDecimal
        var prevFloatFeeAdjustedSynthetic: BigDecimal
        if (isHead) {
            logger.trace("is head")
            //如果是头部节点，prev 为 null
            //实际上 prevSynthetic 是 1
            prevSynthetic = BigDecimal.ONE
            prevFeeAdjustedSynthetic = BigDecimal.ONE
            prevFloatFeeAdjustedSynthetic = BigDecimal.ONE
        } else {
            logger.trace("prev is $prev")
            prevSynthetic = prev!!.syntheticPrice
            prevFeeAdjustedSynthetic = prev!!.feeAdjustedSyntheticPrice
            prevFloatFeeAdjustedSynthetic = prev!!.floatFeeAdjustedSyntheticPrice
        }
        logger.trace("prev SP is $prevSynthetic")
        logger.trace("prev FASP is $prevFeeAdjustedSynthetic")
        logger.trace("BP is $bestPrice")
        if (isBid) {
            syntheticPrice = prevSynthetic.divide(bestPrice, MathContext.DECIMAL64)
            feeAdjustedSyntheticPrice = prevFeeAdjustedSynthetic.divide(bestFeeAdjustedPrice, MathContext.DECIMAL64)
            floatFeeAdjustedSyntheticPrice = prevFloatFeeAdjustedSynthetic.divide(
                bestFloatFeeAdjustedPrice,
                MathContext.DECIMAL64
            )
        } else {
            syntheticPrice = prevSynthetic.multiply(bestPrice, MathContext.DECIMAL64)
            feeAdjustedSyntheticPrice = prevFeeAdjustedSynthetic.multiply(bestFeeAdjustedPrice, MathContext.DECIMAL64)
            floatFeeAdjustedSyntheticPrice = prevFloatFeeAdjustedSynthetic.multiply(
                bestFloatFeeAdjustedPrice,
                MathContext.DECIMAL64
            )
        }
        logger.trace("After updateSyntheticPrice SP is $syntheticPrice FASP is $feeAdjustedSyntheticPrice")
        //传递到后续节点，继续更新 synthetic 价格
        logger.trace("succ is $succ")
        succ?.updateSyntheticPrice()
    }
}

data class ArbitrageSymbolNode(val symbol: Symbol) {

    @org.springframework.data.annotation.Transient
    val arbitrageNodeMap: MutableMap<ExchangeName, ArbitrageExchangeSymbolNode> = mutableMapOf()

    @org.springframework.data.annotation.Transient
    val pathNodeList: MutableList<ArbitrageCycleNode> = mutableListOf()

    fun newPathNode(isBid: Boolean): ArbitrageCycleNode {
        val apn = ArbitrageCycleNode(isBid, this)
        pathNodeList.add(apn)
        return apn
    }

    fun updateFee(exchange: ExchangeName, fee: BigDecimal) {
        for (pathNode in pathNodeList) {
            arbitrageNodeMap[exchange]?.feeRate = fee
        }
    }

    fun updateBBO(exchange: ExchangeName, bbo: BBO) {
        for (pathNode in pathNodeList) {
            pathNode.updatePrice(exchange, bbo)
            if (pathNode.isHead) {
                //更新头结点的价格时，必定会传播到尾结点，
                //如果中途的结点是 holding，则可以改成 working，如果是 blank，那么停止修改它及其后续结点的 state
                var itr: ArbitrageCycleNode? = pathNode
                var last: ArbitrageCycleNode = pathNode
                while (itr != null && itr.state == PathNodeState.HOLDING) {
                    last = itr
                    itr = itr.succ
                }
                if (itr == null) {
                    //该循环子节点全是 holding
                    last.state = PathNodeState.WORKING
                }
            }
        }
    }
}