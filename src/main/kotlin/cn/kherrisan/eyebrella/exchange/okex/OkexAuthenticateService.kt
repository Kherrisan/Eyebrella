package cn.kherrisan.eyebrella.exchange.okex

import cn.kherrisan.eyebrella.core.common.*
import cn.kherrisan.eyebrella.core.http.AuthenticationService
import com.google.gson.GsonBuilder
import java.util.*

class OkexAuthenticateService(val host: String) : AuthenticationService {

    override fun signedHttpRequest(method: String, path: String, params: MutableMap<String, Any>, headers: MutableMap<String, String>) {
        val apiKey = SpringContainer[OkexRuntimeConfig::class].apiKey
        val apiSecret = SpringContainer[OkexRuntimeConfig::class].secretKey
        val password = SpringContainer[OkexRuntimeConfig::class].password
        val subPath = path.removePrefix(host)
        val ts = iso8601WithMS()
        var payload = "${ts}${method}$subPath"
        if (method.toUpperCase() == GET && params.isNotEmpty())
            payload = "$payload?${urlEncode(params)}"
        if (method.toUpperCase() == POST && params.isNotEmpty())
            payload = "${payload}${GsonBuilder().create().toJson(params)}"
        headers["OK-ACCESS-SIGN"] = Base64.getEncoder().encodeToString(hmacSHA256Signature(payload, apiSecret!!))
        headers["OK-ACCESS-KEY"] = apiKey!!
        headers["OK-ACCESS-TIMESTAMP"] = ts.toString()
        headers["OK-ACCESS-PASSPHRASE"] = password!!
    }

    override fun signWebsocketRequest(method: String, path: String, params: MutableMap<String, Any>) {
        val runtimeConfig = SpringContainer[OkexRuntimeConfig::class]
        params["api_key"] = runtimeConfig.apiKey!!
        params["passphrase"] = runtimeConfig.password!!
        params["timestamp"] = "${System.currentTimeMillis().toFloat() / 1000}"
        val payload = "${params["timestamp"]}${method}${path}"
        params["sign"] = Base64.getEncoder().encodeToString(hmacSHA256Signature(payload, runtimeConfig.secretKey!!))
    }
}