package cn.kherrisan.eyebrella.core.http

import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse

interface SignedHttpService : HttpService {
    suspend fun signedGet(url: String, params: MutableMap<String, String> = mutableMapOf(), headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    suspend fun signedPost(url: String, httpMediaTypeEnum: HttpMediaTypeEnum = HttpMediaTypeEnum.JSON, params: MutableMap<String, Any> = mutableMapOf(), headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    suspend fun signedDelete(url: String, httpMediaTypeEnum: HttpMediaTypeEnum = HttpMediaTypeEnum.JSON, params: MutableMap<String, Any> = mutableMapOf(), headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    suspend fun signedJsonPost(url: String, params: MutableMap<String, Any>, headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    suspend fun signedUrlencodedPost(url: String, params: MutableMap<String, Any>, headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    suspend fun signedUrlencodedDelete(url: String, params: MutableMap<String, Any>, headers: MutableMap<String, String> = mutableMapOf()): HttpResponse<Buffer>
    fun auth(): AuthenticationService
}
