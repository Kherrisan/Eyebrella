package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.core.DefaultWebsocketDispatcher
import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.http.HttpMediaTypeEnum
import cn.kherrisan.eyebrella.core.http.HttpService
import cn.kherrisan.eyebrella.core.http.VertxHttpService
import cn.kherrisan.eyebrella.core.websocket.AbstractWebsocketDispatcher
import cn.kherrisan.eyebrella.entity.CurrencyMetaInfo
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import java.nio.charset.StandardCharsets
import javax.annotation.PostConstruct

abstract class AbstractSpotMarketService(val staticConfig: ExchangeStaticConfiguration,
                                         val dataAdaptor: ServiceDataAdaptor,
                                         val metaInfo: ExchangeMetaInfo)
    : SpotMarketService
        , HttpService
        , ServiceDataAdaptor by dataAdaptor
        , IHttpUtils by HttpUtils()
        , CoroutineScope by TopCoroutineScope() {

    /**
     * Websocket 消息发送和分发器，子类覆盖时必须 Autowired
     */
    open val dispatcher: AbstractWebsocketDispatcher = DefaultWebsocketDispatcher()

    @Autowired
    private lateinit var http: VertxHttpService

    @Autowired
    private lateinit var vertx: Vertx

    @PostConstruct
    fun initMetaInfo() {
        runBlocking {
            for (i in listOf(
                    initCurrencyList(),
                    initSymbolMetaInfo()
            )) {
                i.join()
            }
        }
    }

    suspend fun CoroutineScope.initCurrencyList(): Job = launch(vertx.dispatcher()) {
        metaInfo.currencyList = getCurrencies()
    }

    suspend fun CoroutineScope.initSymbolMetaInfo(): Job = launch(vertx.dispatcher()) {
        getSymbolMetaInfo().forEach {
            metaInfo.symbolMetaInfo[it.symbol] = it
            if (!metaInfo.currencyMetaInfo.containsKey(it.symbol.base)) {
                metaInfo.currencyMetaInfo[it.symbol.base] = CurrencyMetaInfo(it.symbol.base, 0)
            }
            metaInfo.currencyMetaInfo[it.symbol.base]!!.smallerSizeIncrement(it.sizeIncrement)
            if (!metaInfo.currencyMetaInfo.containsKey(it.symbol.quote)) {
                metaInfo.currencyMetaInfo[it.symbol.quote] = CurrencyMetaInfo(it.symbol.quote, 0)
            }
            metaInfo.currencyMetaInfo[it.symbol.quote]!!.smallerSizeIncrement(it.volumeIncrement)
        }
    }

    val logger = LogManager.getLogger()

    public fun publicUrl(subPath: String): String {
        return "${staticConfig.spotMarketHttpHost}${subPath}"
    }

    override suspend fun get(path: String, params: Map<String, String>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        return http.get(path, params, headers)
    }

    override suspend fun post(path: String, httpMediaTypeEnum: HttpMediaTypeEnum, params: Map<String, Any>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        return http.post(path, httpMediaTypeEnum, params, headers)
    }

    override suspend fun delete(path: String, httpMediaTypeEnum: HttpMediaTypeEnum, params: Map<String, Any>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        return http.delete(path, httpMediaTypeEnum, params, headers)
    }

    fun jsonObject(resp: HttpResponse<Buffer>): JsonObject {
        val e = checkResponse(resp)
        return e.asJsonObject
    }

    fun jsonArray(resp: HttpResponse<Buffer>): JsonArray {
        val e = checkResponse(resp)
        return e.asJsonArray
    }

    open fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val t = resp.body().toString(StandardCharsets.UTF_8)
        return JsonParser.parseString(t)
    }
}