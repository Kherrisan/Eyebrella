package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.AbstractSpot
import cn.kherrisan.eyebrella.core.SubscriptionHandler
import cn.kherrisan.eyebrella.core.TradingDataListener
import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.enumeration.AccountTypeEnum
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.data.SpotBalance
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.util.AttributeKey
import io.vertx.core.Promise
import org.apache.commons.collections.buffer.CircularFifoBuffer
import org.apache.logging.log4j.LogManager
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

val SPOT_BALANCE_MODEL_ATTR = AttributeKey.newInstance<String>("model")

class HuobiTradingPingPongHander : SimpleChannelInboundHandler<HuobiTradingPing>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HuobiTradingPing?) {
        ctx!!.writeAndFlush(HuobiTradingPing("pong", msg!!.ts))
    }
}

class HuobiMarketPingPongHandler : SimpleChannelInboundHandler<HuobiPing>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HuobiPing?) {
        ctx!!.writeAndFlush(HuobiPong(msg!!.ping))
    }
}

class HuobiAuthenticationHandler(private val promise: Promise<Channel>) :
    SimpleChannelInboundHandler<HuobiAuthenticationResponse>() {

    private val logger = LogManager.getLogger()

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HuobiAuthenticationResponse?) {
        if (msg!!.error_code == 0) {
            logger.debug("${ctx!!.channel().localAddress()} 鉴权成功 $msg")
            ctx.pipeline().remove(this)
            promise.complete(ctx.channel())
        } else {
            logger.error("鉴权失败")
            logger.error(msg)
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        val runtime = SpringContainer[HuobiRuntimeConfig::class]
        val params = mutableMapOf<String, Any>()
        params["AccessKeyId"] = runtime.apiKey!!
        params["SignatureVersion"] = "2"
        params["SignatureMethod"] = "HmacSHA256"
        params["Timestamp"] = gmt()
        val sb = StringBuilder(1024)
        sb.append(GET).append('\n')
            .append("api.huobi.pro").append('\n')
            .append("/ws/v1").append('\n')
            .append(sortedUrlEncode(params))
        params["Signature"] =
            Base64.getEncoder().encodeToString(hmacSHA256Signature(sb.toString(), runtime.secretKey!!))
        params["op"] = "auth"
        ctx!!.channel().writeAndFlush(params)
    }
}

class HuobiTradingMessageDecoder : MessageToMessageDecoder<JsonObject>(),
    ServiceDataAdaptor by SpringContainer[HuobiServiceDataAdaptor::class] {

    private val logger = LogManager.getLogger()
    private val metaInfo = SpringContainer[HuobiMetaInfo::class]

    override fun decode(ctx: ChannelHandlerContext?, msg: JsonObject?, out: MutableList<Any>?) {
        out!!
        val op = msg!!["op"].asString
        when (op) {
            "ping" -> {
                //ping-pong
                out.add(Gson().fromJson(msg, HuobiTradingPing::class.java))
            }
            "auth" -> {
                //鉴权的响应
                //鉴权成功的响应格式：{"op":"auth","ts":1581497756963,"err-code":0,"data":{"user-id":5691027}}
                //ch:"auth"
                out.add(Gson().fromJson(msg, HuobiAuthenticationResponse::class.java))
            }
            "sub" -> {
                //订阅的响应
                out.add(Gson().fromJson(msg, HuobiTradingSubscriptionResponse::class.java))
            }
            "unsub" -> {
                //取消订阅的响应
                out.add(Gson().fromJson(msg, HuobiTradingSubscriptionResponse::class.java))
            }
            "notify" -> {
                //推送的数据
                val ch = msg["topic"].asString
                when {
                    ch.contains("accounts") -> out.addAll(parseBalance(msg))
                    ch.contains("orders") -> out.add(parseOrderDeal(msg))
                }
            }
        }
    }

    private fun parseBalance(obj: JsonObject): List<SpotBalance> {
        val time = MyDate(obj["ts"].asLong)
        return obj["data"].asJsonObject["list"].asJsonArray.map { it.asJsonObject }
            .filter { it["account-id"].asInt == metaInfo.accountIdMap[AccountTypeEnum.SPOT]!!.toInt() }
            .filter { it["type"].asString == "trade" }
            .map {
                SpotBalance(
                    ExchangeName.HUOBI,
                    currency(it["currency"]),
                    size(it["balance"], currency(it["currency"])),
                    0f.toBigDecimal(),
                    time
                )
            }
    }

    private fun parseOrderDeal(obj: JsonObject): SpotOrderDeal {
        val time = MyDate(obj["ts"].asLong)
        val data = obj["data"].asJsonObject
        val sym = symbol(data["symbol"])
        return SpotOrderDeal(
            data["match-id"].asLong.toString(),
            data["order-id"].asLong.toString(),
            sym,
            orderState(data["order-state"]),
            tradeRole(data["role"]),
            price(data["price"], sym),
            size(data["filled-amount"], sym),
            0f.toBigDecimal(),
            time
        )

    }
}

class HuobiMarketMessageDecoder : MessageToMessageDecoder<JsonObject>(),
    ServiceDataAdaptor by SpringContainer[HuobiServiceDataAdaptor::class] {

    private val logger = LogManager.getLogger()

    override fun decode(ctx: ChannelHandlerContext?, msg: JsonObject?, out: MutableList<Any>?) {
        out!!
        when {
            msg!!.has("subbed") -> out.add(Gson().fromJson(msg, HuobiMarketSubscriptionResponse::class.java))
            msg.has("unsub") -> {
                //暂时不处理取消订阅的情况
            }
            msg.has("ping") -> out.add(Gson().fromJson(msg, HuobiPing::class.java))
            msg.has("ch") -> {
                val ch = msg["ch"].asString
                when {
                    ch.contains("mbp") -> out.add(parseIncrementMBP(msg))
                    ch.contains("kline") -> out.add(parseKline(msg))
                    ch.contains("depth") -> out.add(parseDepth(msg))
                    ch.contains("bbo") -> out.add(parseBBO(msg))
                }
            }
            msg.has("rep") -> out.add(parseMBP(msg))
        }
    }

    private fun parseBBO(obj: JsonObject): BBO {
        val symbol = symbol(obj["ch"].asString.split(".")[1])
        val tick = obj["tick"].asJsonObject
        val ts = MyDate(obj["ts"].asLong)
        return BBO(
            symbol,
            ExchangeName.HUOBI,
            price(tick["ask"], symbol),
            size(tick["askSize"], symbol),
            price(tick["bid"], symbol),
            size(tick["bidSize"], symbol),
            ts
        )
    }

    /**
     * val channel = "market.${symbol.nameWithoutSlash()}.depth.step0"
    return dispatcher.newSubscription<Depth>(channel) { resp, sub ->
    val tick = resp.asJsonObject["tick"].asJsonObject
    val askMap = HashMap<BigDecimal, BigDecimal>()
    val bidMap = HashMap<BigDecimal, BigDecimal>()
    tick["asks"].asJsonArray.map { it.asJsonArray }.forEach {
    askMap[price(it[0], symbol)] = size(it[1], symbol)
    }
    tick["bids"].asJsonArray.map { it.asJsonArray }.forEach {
    bidMap[price(it[0], symbol)] = size(it[1], symbol)
    }
    sub.deliver(Depth(symbol,
    date(resp.asJsonObject["ts"].asLong.toString()),
    askMap, bidMap
    ))
    }.subscribe()
     */
    private fun parseDepth(obj: JsonObject): Depth {
        val symbol = symbol(obj["ch"].asString.split(".")[1])
        val tick = obj["tick"].asJsonObject
        val ts = MyDate(obj["ts"].asLong)
        val depth = depth(symbol, tick)
        depth.time = ts
        return depth
    }

    private fun parseKline(obj: JsonObject): Kline {
        val symbol = symbol(obj["ch"].asString.split(".")[1])
        val tick = obj.asJsonObject["tick"].asJsonObject
        return Kline(
            symbol,
            date(obj.asJsonObject["ts"].asLong.toString()),
            price(tick["open"], symbol),
            price(tick["close"], symbol),
            price(tick["high"], symbol),
            price(tick["low"], symbol),
            volume(tick["vol"], symbol)
        )
    }

    private fun parseMBP(obj: JsonObject): Depth {
        val symbol = symbol(obj["rep"].asString.split(".")[1])
        var depth = depth(symbol, obj.asJsonObject["data"].asJsonObject)
        depth = SequentialDepth(depth, 0L, 0L)
        depth.seq = obj.asJsonObject["data"].asJsonObject["seqNum"].asLong
        return depth
    }

    private fun parseIncrementMBP(obj: JsonObject): SequentialDepth {
        val symbol = symbol(obj["ch"].asString.split(".")[1])
        var depth = depth(symbol, obj.asJsonObject["tick"].asJsonObject)
        depth = SequentialDepth(depth, 0L, 0L)
        depth.seq = obj.asJsonObject["tick"].asJsonObject["seqNum"].asLong
        depth.prev = obj.asJsonObject["tick"].asJsonObject["prevSeqNum"].asLong
        return depth
    }
}

class HuobiMarketSubscriptionHandler :
    SubscriptionHandler<HuobiMarketSubscriptionResponse, HuobiMarketSubscriptionRequest>() {

    private val depthIdMap = mutableMapOf<String, String>()

    override fun requestId(req: HuobiMarketSubscriptionRequest): List<String> = listOf(req.id)

    override fun responseId(resp: HuobiMarketSubscriptionResponse): List<String> = listOf(resp.id)

    override fun preDecode(
        ctx: ChannelHandlerContext?,
        msg: HuobiMarketSubscriptionResponse,
        out: MutableList<Any>?
    ): Boolean {
        if (msg.subbed.contains("mbp")) {
            return false
        }
        return true
    }

    override fun preEncode(
        ctx: ChannelHandlerContext?,
        msg: HuobiMarketSubscriptionRequest,
        out: MutableList<Any>?
    ): Boolean {
        if (msg.sub.contains("mbp")) {
            depthIdMap[msg.sub] = msg.id
        }
        return true
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is Depth) {
            val depth = "market.${evt.symbol.nameWithoutSlash()}.mbp.150"
            idPromiseMap[depthIdMap[depth]]!!.complete()
            logger.debug("${evt.symbol} 深度数据连续，订阅成功")
        }
    }

    override fun isSubscriptionSuccess(msg: HuobiMarketSubscriptionResponse): Boolean {
        return msg.status == "ok"
    }
}

class HuobiSpotKlineHandler(val spot: AbstractSpot) : SimpleChannelInboundHandler<Kline>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: Kline?) {
        spot.updateKline(msg!!)
    }
}

class HuobiSpotDepthHandler(val spot: AbstractSpot) : SimpleChannelInboundHandler<Depth>() {

    private val logger = LogManager.getLogger()
    private val seqDepthBufferMap: MutableMap<Symbol, CircularFifoBuffer> = HashMap()
    private val baseDepthMap: MutableMap<Symbol, SequentialDepth> = HashMap()
    private val disorderCounterMap: MutableMap<Symbol, Int> = HashMap()
    private val stableWorkingMap: MutableMap<Symbol, Boolean> = HashMap()

    private fun handleUncontinuedDepth(seqDepth: SequentialDepth) {
        val buffer = seqDepthBufferMap[seqDepth.symbol]!!
        val baseDepth = baseDepthMap[seqDepth.symbol]!!
        //增量深度数据出现缺失，但也有可能只是暂时的失序，可以等一段时间
        //但要记录等待的次数，避免一直处于失序-等待的状态中
        //如果等待次数过多，说明永远没有可能获得连续数据了，应该重新请求全量数据。
//        logger.debug("${seqDepth.symbol} 增量深度数据不连续")
        val c = disorderCounterMap.getOrPut(seqDepth.symbol) { 0 }
        disorderCounterMap[seqDepth.symbol] = c + 1
        if (c + 1 >= 10) {
            logger.error("${seqDepth.symbol} 的增量深度数据不连续过多，重发全量数据请求。")
            //再请求一次全量数据，在此之前，把buffer中所有的prev大于base.seq的都删掉
            val before = buffer.map { it as SequentialDepth }.filter { it.prev < baseDepth.seq }
            buffer.remove(before)
            (spot as HuobiSpot).requestBaseDepth(seqDepth.symbol)
            //baseDepthMap 中删除该全量数据，表示已经请求了全量数据，但还没有到达，避免频繁地请求全量数据
            baseDepthMap.remove(seqDepth.symbol)
        }
    }

    private fun tryMergeSequentialDepth(ctx: ChannelHandlerContext, seqDepth: SequentialDepth) {
        if (seqDepth.symbol in baseDepthMap) {
            var updated = false
            val baseDepth = baseDepthMap[seqDepth.symbol]!!
            val buffer = seqDepthBufferMap[seqDepth.symbol]!!
            val afterInitDepth =
                buffer.map { it as SequentialDepth }.filter { it.prev >= baseDepth.seq }.sortedBy { it.seq }
            for (inc in afterInitDepth) {
                if (inc.prev == baseDepth.seq) {
                    baseDepth.merge(inc)
                    baseDepth.seq = inc.seq
                    updated = true
                    disorderCounterMap[seqDepth.symbol] = 0
                    buffer.remove(inc)
                } else {
                    //此时数据不连续，并且此刻没有请求全量数据
                    handleUncontinuedDepth(seqDepth)
                    break
                }
                //else：数据不连续，并且已经请求了全量数据
            }
            if (updated) {
                if (!stableWorkingMap.getOrDefault(baseDepth.symbol, false)) {
                    stableWorkingMap[baseDepth.symbol] = true
                    ctx.fireUserEventTriggered(baseDepth)
                }
                spot.updateDepth(baseDepth.copy())
            }
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: Depth?) {
//        logger.debug(msg)
        if (msg is SequentialDepth) {
            val seqDepth = msg as SequentialDepth
            if (seqDepth.prev == 0L) {
                //是全量数据
                logger.debug("获得 ${msg.symbol} 全量深度，序号是 ${(msg as SequentialDepth).seq}")
                baseDepthMap[seqDepth.symbol] = seqDepth
            } else {
                //是增量数据
                if (seqDepth.symbol !in seqDepthBufferMap) {
                    seqDepthBufferMap[seqDepth.symbol] = CircularFifoBuffer(128)
                }
                //不管此时全量数据有没有到，都要把增量数据放到缓存中
                seqDepthBufferMap[seqDepth.symbol]!!.add(seqDepth)
                tryMergeSequentialDepth(ctx!!, seqDepth)
            }
        } else {
            //MBP depth
            spot.updateDepth(msg!!)
        }
    }
}

class HuobiSpotOrderDealHandler(val spot: TradingDataListener) : SimpleChannelInboundHandler<SpotOrderDeal>() {

    private val logger = LogManager.getLogger()

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: SpotOrderDeal?) {
        try {
            spot.updateOrderDeal(msg!!)
        } catch (e: Exception) {
            logger.error(e)
            e.printStackTrace()
        }
    }
}

@ChannelHandler.Sharable
class HuobiSpotBalanceHandler(val spot: AbstractSpot) : SimpleChannelInboundHandler<SpotBalance>() {

    private val logger = LogManager.getLogger()
    private val freeBalanceMap: MutableMap<Currency, SpotBalance> = HashMap()
    private val includeFrozenBalanceMap: MutableMap<Currency, SpotBalance> = HashMap()
    private val lastFreeBalanceMap: MutableMap<Currency, SpotBalance> = HashMap()
    private val lastIncludeFrozenBalanceMap: MutableMap<Currency, SpotBalance> = HashMap()

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: SpotBalance?) {
        val model = ctx!!.channel().attr(SPOT_BALANCE_MODEL_ATTR).get()
        msg!!
        logger.trace("${model}-${msg}")
        if (model == "0") {
            if (msg.currency in lastFreeBalanceMap) {
                val last = lastFreeBalanceMap[msg.currency]!!
                if (last.free.compareTo(msg.free) == 0 && last.frozen.compareTo(msg.frozen) == 0) {
                    //这个 spotBalance 和上一个在数值上相同，跳过这个
                    return
                }
            }
            freeBalanceMap[msg.currency] = msg
        } else {
            if (msg.currency in lastIncludeFrozenBalanceMap) {
                val last = lastIncludeFrozenBalanceMap[msg.currency]!!
                if (last.free.compareTo(msg.free) == 0 && last.frozen.compareTo(msg.frozen) == 0) {
                    //这个 spotBalance 和上一个在数值上相同，跳过这个
                    return
                }
            }
            includeFrozenBalanceMap[msg.currency] = msg
        }
        var balanceUpdated = false
        val c = freeBalanceMap.iterator()
        while (c.hasNext()) {
            val freeEntry = c.next()
            if (freeEntry.key in includeFrozenBalanceMap) {
                lastFreeBalanceMap[freeEntry.key] = freeEntry.value
                lastIncludeFrozenBalanceMap[freeEntry.key] = includeFrozenBalanceMap[freeEntry.key]!!
                val includeFrozen = includeFrozenBalanceMap[freeEntry.key]!!
                freeEntry.value.frozen = includeFrozen.free - freeEntry.value.free
                spot.updateBalance(freeEntry.value)
                balanceUpdated = true
                c.remove()
                includeFrozenBalanceMap.remove(freeEntry.key)
            }
        }
    }
}

class HuobiTradingSubscriptionHandler :
    SubscriptionHandler<HuobiTradingSubscriptionResponse, HuobiTradingSubscriptionRequest>() {

    override fun requestId(req: HuobiTradingSubscriptionRequest): List<String> = listOf(req.cid)

    override fun responseId(resp: HuobiTradingSubscriptionResponse): List<String> = listOf(resp.cid)

    override fun isSubscriptionSuccess(msg: HuobiTradingSubscriptionResponse): Boolean {
        return msg.error_code == 0
    }
}

class HuobiSpotBBOHandler(val spot: AbstractSpot) : SimpleChannelInboundHandler<BBO>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: BBO?) {
        spot.updateBBO(msg!!)
    }
}