package cn.kherrisan.eyebrella.core

import cn.kherrisan.eyebrella.Eyebrella
import cn.kherrisan.eyebrella.arbitrage.Arbitrage
import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.enumeration.OrderSideEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderStateEnum
import cn.kherrisan.eyebrella.core.enumeration.OrderTypeEnum
import cn.kherrisan.eyebrella.core.service.SpotMarketService
import cn.kherrisan.eyebrella.core.websocket.AbstractChannelInitializer
import cn.kherrisan.eyebrella.core.websocket.WebsocketClient
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.SpotBalance
import cn.kherrisan.eyebrella.entity.data.SpotOrder
import cn.kherrisan.eyebrella.entity.data.SpotTradingFee
import cn.kherrisan.eyebrella.exchange.huobi.HuobiSpot
import cn.kherrisan.eyebrella.repository.VertxDepthRepository
import cn.kherrisan.eyebrella.repository.VertxFeeRepository
import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.*
import org.apache.commons.lang3.time.DateUtils
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import java.lang.Exception
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import kotlin.collections.HashMap

fun main() {
    Eyebrella.init()
    runBlocking {
        SpringContainer[HuobiSpot::class].buy(BTC_USDT, 20f.toBigDecimal())
    }
}

abstract class AbstractSpot(
    val market: SpotMarketService,
    val trading: SpotTradingService,
    val rc: ExchangeRuntimeConfig,
    val name: ExchangeName
) : Spot, MarketDataListener, TradingDataListener {

    protected var halfRTT: Long? = null
    protected val logger = LogManager.getLogger()
    protected val feeMap: MutableMap<Symbol, SpotTradingFee> = ConcurrentHashMap()
    protected val openOrders: MutableMap<String, SpotOrder> = ConcurrentHashMap()
    protected val openOrderPromiseMap: MutableMap<String, Promise<Any>> = ConcurrentHashMap()
    protected val balance: MutableMap<Currency, SpotBalance> = ConcurrentHashMap()
    protected val depthMap: MutableMap<Symbol, Depth> = ConcurrentHashMap()
    protected val klineMap: MutableMap<Symbol, Kline> = ConcurrentHashMap()
    protected val bboMap: MutableMap<Symbol, BBO> = ConcurrentHashMap()
    protected var symbols: List<Symbol>? = null
    protected var currencys: List<Currency>? = null
    private var symbolMetaInfoMap: MutableMap<Symbol, SymbolMetaInfo>? = null
    private val vertx = VertxContainer.vertx()
    protected val wsClientList: MutableList<WebsocketClient> = LinkedList()
    val workingSymbols = mutableListOf<Symbol>()
    protected lateinit var bootstrap: SpotBootstraper

    @Autowired
    protected lateinit var feeRepository: VertxFeeRepository

    @Autowired
    protected lateinit var depthRepository: VertxDepthRepository

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    @PostConstruct
    fun init() {
        bootstrap = SpotBootstraper()
        bootstrap.boot()
    }

    open suspend fun CoroutineScope.subscribeAll() {
        logger.debug("开始订阅所有内容")
        symbols().forEach {
            launch {
                try {
                    subscribeBBO(it)
                    workingSymbols.add(it)
                    delay(50)
                } catch (e: Exception) {
                    println(e)
                }
            }
        }
        launch {
            subscribeBalance(BTC)
        }
        launch {
            subscribeSpotOrderDeal(BTC_USDT)
        }
    }

    override fun bbo(symbol: Symbol): BBO {
        return bboMap[symbol]!!
    }

    override fun bestBid(symbol: Symbol): DepthItem {
        val bbo = bboMap[symbol]!!
        return DepthItem(bbo.bid, bbo.bidAmount)
    }

    override fun bestAsk(symbol: Symbol): DepthItem {
        val bbo = bboMap[symbol]!!
        return DepthItem(bbo.ask, bbo.askAmount)
    }

    override fun symbols(): List<Symbol> = symbols ?: runBlocking { market.getSymbols() }
//    override fun symbols(): List<Symbol> = listOf(
//        BTC_USDT,
//        ETH_USDT,
//        ETH_BTC
//    )

    override fun currencys(): List<Currency> = currencys ?: runBlocking { market.getCurrencies() }

    override fun metaInfo(symbol: Symbol): SymbolMetaInfo = symbolMetaInfoMap!![symbol]!!

    override suspend fun depth(symbol: Symbol): Depth {
        return market.getDepths(symbol)
    }

    override suspend fun freeBalance(currency: Currency): BigDecimal? {
        return balance[currency]?.free
    }

    protected suspend fun updateTradingFee(symbol: Symbol) {
        feeMap[symbol] = trading.getFee(symbol)
        feeRepository.save(feeMap[symbol]!!)
    }

    open fun isFeeRetired(fee: SpotTradingFee): Boolean = !DateUtils.isSameDay(fee.time, Date())

    override suspend fun fee(symbol: Symbol): SpotTradingFee {
        var fee = feeMap[symbol]
        if (fee == null || isFeeRetired(fee)) {
            fee = feeRepository.getLatest(name, symbol)
            if (fee == null || isFeeRetired(fee)) {
                fee = trading.getFee(symbol)
                feeRepository.save(fee)
            }
            feeMap[symbol] = fee
        }
        return fee
    }

    override suspend fun buy(symbol: Symbol, amount: BigDecimal, price: BigDecimal?): TransactionResult {
        val buyResult = if (price == null) {
            trading.marketBuy(symbol, volume = amount)
        } else {
            trading.limitBuy(symbol, price, amount)
        }
        val p = Promise.promise<Any>()
        if (buyResult.tid in openOrders) {
            when (openOrders[buyResult.tid]!!.state) {
                OrderStateEnum.FILLED -> {
                    logger.info("下单 ${buyResult.tid} 成功")
                    return buyResult
                }
                OrderStateEnum.FAILED -> error("创建订单失败：$buyResult")
                else -> {
                }
            }
        }
        openOrderPromiseMap[buyResult.tid] = p
        p.future().await()
        logger.info("下单 ${symbol} ${buyResult.tid} 成功，下单价格为 $price，下单数量为 $amount")
        return buyResult
    }

    override suspend fun sell(symbol: Symbol, amount: BigDecimal, price: BigDecimal?): TransactionResult {
        val sellResult = if (price == null) {
            trading.marketSell(symbol, amount)
        } else {
            trading.limitSell(symbol, price, amount)
        }
        val p = Promise.promise<Any>()
        if (sellResult.tid in openOrders) {
            when (openOrders[sellResult.tid]!!.state) {
                OrderStateEnum.FILLED -> return sellResult
                OrderStateEnum.FAILED -> error("创建订单失败：$sellResult")
                else -> {
                }
            }
        }
        openOrderPromiseMap[sellResult.tid] = p
        p.future().await()
        return sellResult
    }

    override suspend fun cancel(order: SpotOrder): TransactionResult {
        return trading.cancelOrder(order.exOid, order.symbol)
    }

    override fun openOrders(): List<SpotOrder> {
        return openOrders.values.toList()
    }

    override suspend fun orderInfo(oid: String): SpotOrder {
        TODO("Not yet implemented")
    }

    override suspend fun dealInfo(): List<SpotOrderDeal> {
        TODO("Not yet implemented")
    }

    abstract suspend fun subscribeBBO(symbol: Symbol? = null)

    abstract suspend fun subscribeDepth(symbol: Symbol? = null)

    abstract suspend fun subscribeKline(symbol: Symbol? = null)

    abstract suspend fun subscribeBalance(currency: Currency? = null)

    abstract suspend fun subscribeSpotOrderDeal(symbol: Symbol? = null)

    open suspend fun sendSubscriptionMessage(msg: Any, ws: WebsocketClient) {

    }

    override fun updateBBO(bbo: BBO) {
        bboMap[bbo.symbol] = bbo
        runBlocking {
            Arbitrage.updateBBO(name, bbo)
        }
    }

    fun updateDepth(newDepth: Depth) {
        depthMap[newDepth.symbol] = newDepth
    }

    fun updateKline(newKline: Kline) {
        klineMap[newKline.symbol] = newKline
    }

    override fun updateBalance(newBalance: SpotBalance) {
        logger.info("账户 ${newBalance.currency} 余额 ${newBalance.free}，冻结 ${newBalance.frozen}")
        balance[newBalance.currency] = newBalance
    }

    override fun updateOrderDeal(newOrderDeal: SpotOrderDeal) {
        logger.info("订单 ${newOrderDeal.oid}(${newOrderDeal.symbol}) 状态变化为 ${newOrderDeal.state}")
        if (newOrderDeal.did !in openOrders) {
            openOrders[newOrderDeal.oid] = SpotOrder(
                name,
                newOrderDeal.oid,
                newOrderDeal.symbol,
                newOrderDeal.time,
                0f.toBigDecimal(),
                0f.toBigDecimal(),
                OrderSideEnum.BUY,
                OrderTypeEnum.MARKET,
                newOrderDeal.state
            )
        } else {
            openOrders[newOrderDeal.oid]!!.state = newOrderDeal.state
        }
        if (newOrderDeal.oid in openOrderPromiseMap) {
            when (newOrderDeal.state) {
                OrderStateEnum.FILLED -> openOrderPromiseMap[newOrderDeal.oid]!!.complete()
                OrderStateEnum.FAILED -> openOrderPromiseMap[newOrderDeal.oid]!!.fail("订单失败 ${newOrderDeal.toString()}")
                else -> {
                }
            }
        }
    }

    fun buildWebsocketClient(initializer: AbstractChannelInitializer): WebsocketClient {
        val ws = WebsocketClient(initializer, this)
        wsClientList.add(ws)
        return ws
    }

    abstract suspend fun updateHalfRTT()

    inner class SpotBootstraper : AbstractBootstraper() {

        private suspend fun CoroutineScope.initializeSymbolCurrencyFee() {
            launch {
                symbols = market.getSymbols()
                logger.debug("获得交易对列表 $symbols")
                currencys = market.getCurrencies()
                logger.debug("获得货币列表 $currencys")
                initializeFee()
            }
        }

        private suspend fun CoroutineScope.initializeBalance() {
            launch {
                trading.getBalance().forEach { c, b ->
                    balance[c] = b
                }
            }
        }

        private suspend fun CoroutineScope.launchPeriodicalCheckRTT() {
            launch(backgroundContext.coroutineContext) {
                //定时更新 rtt
                while (true) {
                    updateHalfRTT()
                    delay(10_000)
                }
            }
        }

        private suspend fun CoroutineScope.launchPeriodicalCheckFee() {
            launch(backgroundContext.coroutineContext) {
                while (true) {
                    delay(12 * 60 * 60 * 1000L)
                    logger.debug("启动循环检查手续费")
                    symbols().forEach {
                        updateTradingFee(it)
                        logger.debug("已更新 $name $it 的交易手续费")
                        delay(200)
                    }
                }
            }
        }

        private suspend fun CoroutineScope.initializeFee() {
            launch {
                logger.debug("开始载入手续费数据")
                symbols().forEach {
                    var fee = feeRepository.getLatest(name, it)
                    if (fee == null || isFeeRetired(fee)) {
                        updateTradingFee(it)
                        delay(200)
                    } else {
                        feeMap[it] = fee
                    }
                    logger.debug("载入 ${it} 手续费")
                }
                logger.debug("手续费载入完成")
            }
        }

        override suspend fun CoroutineScope.start() {
            coroutineScope {
                launchPeriodicalCheckRTT()
                initializeSymbolCurrencyFee()
                launch {
                    symbolMetaInfoMap = HashMap()
                    market.getSymbolMetaInfo().forEach { symbolMetaInfoMap!![it.symbol] = it }
                    logger.debug("完成初始化交易所现货元数据")
                }
                launchPeriodicalCheckFee()
                initializeBalance()
            }
            subscribeAll()
        }
    }

    override fun halfRTT(): Long? = halfRTT
}