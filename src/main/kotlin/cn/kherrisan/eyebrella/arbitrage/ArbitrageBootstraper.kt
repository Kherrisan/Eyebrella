package cn.kherrisan.eyebrella.arbitrage

import cn.kherrisan.eyebrella.Eyebrella
import cn.kherrisan.eyebrella.core.AbstractBootstraper
import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.Exchanges
import cn.kherrisan.eyebrella.core.common.ExchangeName
import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.core.common.intId
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.ArbitrageCycleDocument
import cn.kherrisan.eyebrella.repository.ArbitrageCycleDocumentRepository
import cn.kherrisan.eyebrella.repository.BBORepository
import cn.kherrisan.eyebrella.repository.VertxDepthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.collections.HashSet

fun main() {
    Eyebrella.init()
    SpringContainer[ArbitrageBootstraper::class].boot()
}

fun Currency.size(exchange: ExchangeName, s: BigDecimal): BigDecimal {
    return Exchanges[exchange].adaptor.size(s, this)
}

@Component
class ArbitrageBootstraper @Autowired constructor(
    val config: ArbitrageConfig,
    val bboRepository: BBORepository,
    val documentRepository: ArbitrageCycleDocumentRepository,
    val depthRepository: VertxDepthRepository,
    val mongoTemplate: MongoTemplate
) : AbstractBootstraper() {

    private val maxCycleSet: MutableSet<ArbitrageCycle> = HashSet()
    private var counter = 0

    override suspend fun CoroutineScope.start() {
        prepareArbitrage()
        launchPeriodicalCheckingJob()
    }

    private suspend fun CoroutineScope.launchPeriodicalCheckingJob() {
        launch(backgroundContext.coroutineContext) {
            while (true) {
                checkCyclesProfitable()
                delay(100)
            }
        }
        launch(backgroundContext.coroutineContext) {
            try {
                while (true) {
                    val newCycles = Arbitrage.maxN(
                        config.syntheticMin!!.toBigDecimal(),
                        config.syntheticMax!!.toBigDecimal()
                    ).filter { checkIfSameExchange(it) }.subtract(maxCycleSet)
                    for (cycle in newCycles) {
                        cycle.time = MyDate()
                        handleNewCycle(cycle)
                    }
                    delay(10)
                }
            } catch (e: Exception) {
                logger.error(e)
                e.printStackTrace()
            }
        }
    }

    private suspend fun CoroutineScope.executeArbitrage(cycle: ArbitrageCycle) {
        val startTime = System.currentTimeMillis()
        val exchange = cycle.head?.bestArbitrageExchange!!
        logger.info("开始在 $exchange 完成交易 $cycle")
        val spot = Exchanges[exchange].spot
        var itr = cycle.head
        var asset = USDT
        var tradingAmount: BigDecimal? = null
        var originAmount: BigDecimal = spot.freeBalance(asset)!!
        while (itr != null) {
            if (tradingAmount == null) {
                tradingAmount = originAmount.divide(3f.toBigDecimal(), MathContext.DECIMAL64)
                tradingAmount = USDT.size(exchange, tradingAmount)
            } else {
                var currentBalance = spot.freeBalance(asset)
                while (currentBalance == null || currentBalance < tradingAmount / 2f.toBigDecimal()) {
                    currentBalance = spot.freeBalance(asset)
                    delay(5)
                }
                tradingAmount = currentBalance
            }
            logger.info("正在操作 $tradingAmount $asset")
            if (itr.isBid) {
                spot.buy(itr.symbolNode.symbol, tradingAmount)
                asset = itr.symbolNode.symbol.base
                tradingAmount = tradingAmount.divide(itr.bestFeeAdjustedPrice, MathContext.DECIMAL64)
                tradingAmount = itr.symbolNode.symbol.base.size(exchange, tradingAmount)
            } else {
                spot.sell(itr.symbolNode.symbol, tradingAmount)
                asset = itr.symbolNode.symbol.quote
                tradingAmount = tradingAmount.multiply(itr.bestFeeAdjustedPrice, MathContext.DECIMAL64)
                tradingAmount = itr.symbolNode.symbol.quote.size(exchange, tradingAmount)
            }
            itr = itr.succ
        }
        val endTime = System.currentTimeMillis()
        logger.info("完成套利交易，耗时 ${endTime - startTime}ms，USDT 余额 ${spot.freeBalance(asset)}")
    }

    private suspend fun CoroutineScope.checkCyclesProfitable() {
        val itr = maxCycleSet.iterator()
        while (itr.hasNext()) {
            val l = itr.next()
            if (l.tail!!.feeAdjustedSyntheticPrice < BigDecimal.ONE) {
                val period = (MyDate().time - l.time!!.time).toFloat() / 1000
                logger.info("${l} 失效，套利有效时间 $period")
                itr.remove()
            }
        }
    }

    @Document("spot_arbitrage_decision")
    data class ArbitrageSpotOrderDecision(
        val exchange: ExchangeName,
        val direction: OrderSideEnum,
        val price: BigDecimal,
        val amount: BigDecimal,
        val depth: DepthItem,
        val did: Long = intId().toLong()
    ) {

        suspend fun execute() {

        }

    }

    private suspend fun CoroutineScope.generateArbitrageDecisions(cycle: ArbitrageCycle): List<ArbitrageSpotOrderDecision> {
        //还需要核算按照 floatPrice 合成的价格能不到大于 1
        val decisions = mutableListOf<ArbitrageSpotOrderDecision>()
        val exchange = cycle.head!!.bestArbitrageExchange!!
        val spot = Exchanges[exchange].spot
        var itr = cycle.head
        var usdtAsset = spot.freeBalance(USDT)!!
        var amount = usdtAsset.divide(3f.toBigDecimal(), MathContext.DECIMAL64)
        amount = USDT.size(exchange, amount)
        val originAmount = amount
        while (itr != null) {
            val feeRate = spot.fee(itr.symbolNode.symbol).makerFee
            val floatRate = itr.bestArbitrageExchangeFloatRate
            val depth = spot.depth(itr.symbolNode.symbol)
//            depthRepository.save(itr.bestArbitrageExchange!!, depth)
            if (itr.isBid) {
                //买单，需要检查已挂卖单的深度
                //TODO: 这里应该用深度价格还是用 bbo 价格？
                val floatPrice = itr.bestPrice.multiply(BigDecimal.ONE + floatRate, MathContext.DECIMAL64)
                val items = depth.asks.filter { it.price < floatPrice }
                //比 floatPrice 便宜的卖单 都是可以交易的
                //这里需要校验比该卖单价格便宜的所有深度的和 能不能够满足我预定的交易数量
//                val depthAmountSum = items.map { it.amount }.reduce { acc, bigDecimal -> acc + bigDecimal }
//                if(depthAmountSum>amount/floatPrice)
                decisions.add(
                    ArbitrageSpotOrderDecision(
                        itr.bestArbitrageExchange!!,
                        OrderSideEnum.BUY,
                        items.first().price,
                        amount,
                        items.first()
                    )
                )
                //计算买到的 base 的数量，按限价单的价格计算
                amount = amount.divide(floatPrice, MathContext.DECIMAL64)
                //计算扣除手续费之后的实际买到的资产的数量
                amount = amount.multiply(BigDecimal.ONE - feeRate, MathContext.DECIMAL64)
                amount = itr.symbolNode.symbol.base.size(exchange, amount)
            } else {
                val floatPrice = itr.bestPrice.multiply(BigDecimal.ONE - floatRate, MathContext.DECIMAL64)
                val items = depth.bids.filter { it.price > floatPrice }
                decisions.add(
                    ArbitrageSpotOrderDecision(
                        itr.bestArbitrageExchange!!,
                        OrderSideEnum.SELL,
                        items.last().price,
                        amount,
                        items.last()
                    )
                )
                //换成 quote 的数量
                amount = amount.multiply(items.last().price, MathContext.DECIMAL64)
                amount = amount.multiply(BigDecimal.ONE - feeRate, MathContext.DECIMAL64)
                amount = itr.symbolNode.symbol.quote.size(exchange, amount)
            }
            mongoTemplate.save(decisions.last())
            logger.debug(decisions.last())
            itr = itr.succ
        }
        val synthetic = amount.divide(originAmount, MathContext.DECIMAL64).setScale(6, RoundingMode.HALF_UP)
        logger.info("根据滑点价格确定 $cycle 的深度核算得到的合成价格为 $synthetic")
        logger.info("此时 $cycle ${if (cycle.isProfitable()) "有利可图" else "无利可图"}")
        return decisions
    }

    private suspend fun CoroutineScope.handleNewCycle(cycle: ArbitrageCycle) {
        logger.info("发现新的套利循环 ${cycle}，价格为 ${cycle.feeAdjustedSyntheticPrice}")
        logger.info("套利循环 ${cycle.fullString()}")
        var itr = cycle.head
        val bbos = mutableListOf<BBO>()
        while (itr != null) {
            bbos.add(handleCycleNode(itr))
            itr = itr.succ
        }
        val doc = documentRepository.save(cycle.document(bbos))
        if (checkIfSameExchange(cycle)) {
            logger.info("可在 ${cycle.head!!.bestArbitrageExchange} 完成 $doc 中所有交易")
            maxCycleSet.add(cycle)
//            executeArbitrage(cycle)
        }
    }

    private suspend fun CoroutineScope.executeSpotOrder(decisions: List<ArbitrageSpotOrderDecision>) {

    }

    private suspend fun CoroutineScope.checkIfSameExchange(cycle: ArbitrageCycle): Boolean {
        var itr = cycle.head
        while (itr != null) {
            if (itr.bestArbitrageExchange != cycle.head!!.bestArbitrageExchange) {
                return false
            }
            itr = itr.succ
        }
        return true
    }

    private suspend fun recheckCycleByBBO(document: ArbitrageCycleDocument): Boolean {
        var synthetic = BigDecimal.ONE
        var i = 0;
        for (node in document.path) {
            val bbo = document.bbos[i];
            if (node.isBid) {
                synthetic = synthetic.divide(bbo.bid, MathContext.DECIMAL64)
            } else {
                synthetic = synthetic.multiply(bbo.ask, MathContext.DECIMAL64)
            }
            i++;
        }
        logger.info("$document 经过 BBO 数据检验之后的合成价格为 ${synthetic.setScale(6, RoundingMode.HALF_UP)}")
        if (synthetic > document.path.last().feeAdjustedSyntheticPrice) {
            logger.info("BBO 数据检验通过")
            return true
        } else {
            logger.info("BBO 数据检验未通过")
            return false
        }
    }

    private suspend fun CoroutineScope.handleCycleNode(node: ArbitrageCycleNode): BBO {
        val bbo = Exchanges[node.bestArbitrageExchange!!].spot.bbo(node.symbolNode.symbol)
        bboRepository.save(bbo)
        return bbo
    }

    private fun prepareArbitrage() {
        logger.debug("开始寻找套利循环")
        val symbols = mutableListOf<Symbol>()
        for (exchange in Exchanges) {
            symbols.addAll((exchange.spot as AbstractSpot).workingSymbols)
        }
        Arbitrage.build(symbols)
        logger.debug("寻找套利循环完成")
    }
}