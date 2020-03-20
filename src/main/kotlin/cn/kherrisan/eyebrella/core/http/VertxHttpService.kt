package cn.kherrisan.eyebrella.core.http

import cn.kherrisan.eyebrella.core.common.urlEncode
import io.vertx.core.MultiMap
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.awaitResult
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Http 操作的 Vertx 实现类
 *
 * 不维护状态，是单例的。
 * @property vertx Vertx
 * @property client WebClient
 * @constructor
 */
@Component
open class VertxHttpService @Autowired constructor(var vertx: Vertx)
    : HttpService {

    private val client: WebClient
    private val logger = LogManager.getLogger()

    init {
        val clientOptions = WebClientOptions().setTrustAll(true)
        client = WebClient.create(vertx, clientOptions)
    }

    override fun postBody(vararg pairs: Pair<String, Any?>): MutableMap<String, Any> {
        val p = mutableMapOf<String, Any>()
        pairs.forEach {
            if (it.second != null) {
                p[it.first] = it.second!!
            }
        }
        return p
    }

    override fun getBody(vararg pairs: Pair<String, String?>): MutableMap<String, String> {
        val p = mutableMapOf<String, String>()
        pairs.forEach {
            if (it.second != null) {
                p[it.first] = it.second!!
            }
        }
        return p
    }

    override suspend fun get(path: String, params: Map<String, String>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        // TODO:Vertx's addQueryParam(t, u) behave unnormally...
        val url = if (params != null) {
            "$path?${urlEncode(params)}"
        } else {
            path
        }
        val req = client.getAbs(url)
        headers?.forEach { t, u -> req.putHeader(t, u) }
        return awaitResult { req.send(it) }
    }

    override suspend fun delete(path: String, httpMediaTypeEnum: HttpMediaTypeEnum, params: Map<String, Any>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        val req = client.deleteAbs(path)
        headers?.forEach { t, u -> req.putHeader(t, u) }
        return if (params != null) {
            when (httpMediaTypeEnum) {
                HttpMediaTypeEnum.JSON -> awaitResult { req.sendJson(params, it) }
                HttpMediaTypeEnum.URLENCODED -> {
                    val form = MultiMap.caseInsensitiveMultiMap()
                    params.forEach { (t, u) -> form.add(t, u.toString()) }
                    awaitResult { req.sendForm(form, it) }
                }
            }
        } else {
            awaitResult { req.send(it) }
        }
    }

    override suspend fun post(path: String, httpMediaTypeEnum: HttpMediaTypeEnum, params: Map<String, Any>?, headers: Map<String, String>?): HttpResponse<Buffer> {
        val req = client.postAbs(path)
        headers?.forEach { t, u -> req.putHeader(t, u) }
        return if (!params.isNullOrEmpty()) {
            when (httpMediaTypeEnum) {
                HttpMediaTypeEnum.JSON -> awaitResult { req.sendJson(params, it) }
                HttpMediaTypeEnum.URLENCODED -> {
                    val form = MultiMap.caseInsensitiveMultiMap()
                    params.forEach { (t, u) -> form.add(t, u.toString()) }
                    awaitResult { req.sendForm(form, it) }
                }
            }
        } else {
            awaitResult { req.send(it) }
        }
    }
}