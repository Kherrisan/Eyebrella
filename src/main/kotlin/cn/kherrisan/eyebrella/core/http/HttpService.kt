package cn.kherrisan.eyebrella.core.http

import cn.kherrisan.eyebrella.core.common.IHttpUtils
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse

interface HttpService : IHttpUtils {
    suspend fun get(path: String, params: Map<String, String>? = null, headers: Map<String, String>? = null): HttpResponse<Buffer>

    suspend fun post(path: String, httpMediaTypeEnum: HttpMediaTypeEnum = HttpMediaTypeEnum.JSON, params: Map<String, Any>? = null, headers: Map<String, String>? = null): HttpResponse<Buffer>

    suspend fun delete(path: String, httpMediaTypeEnum: HttpMediaTypeEnum = HttpMediaTypeEnum.JSON, params: Map<String, Any>? = null, headers: Map<String, String>? = null): HttpResponse<Buffer>
}