package cn.kherrisan.eyebrella.exchange.kucoin

import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.core.common.hmacSHA256Signature
import cn.kherrisan.eyebrella.core.http.AuthenticationService
import com.google.gson.Gson
import org.apache.commons.codec.binary.Base64

class KucoinAuthenticateService(val host: String) : AuthenticationService {

    override fun signedHttpRequest(method: String, path: String, params: MutableMap<String, Any>, headers: MutableMap<String, String>) {
        val rt = SpringContainer[KucoinService::class.java].runtimeConfig
        headers["KC-API-KEY"] = rt.apiKey!!
        headers["KC-API-TIMESTAMP"] = System.currentTimeMillis().toString()
        headers["KC-API-PASSPHRASE"] = rt.password!!
        var payload = "${headers["KC-API-TIMESTAMP"]}${method.toUpperCase()}${path.removePrefix(host)}"
        if (params.isNotEmpty()) {
            payload = "${payload}${Gson().toJson(params)}"
        }
        val sha = hmacSHA256Signature(payload, rt.secretKey!!)
        headers["KC-API-SIGN"] = Base64.encodeBase64String(sha)
    }

    override fun signWebsocketRequest(method: String, path: String, params: MutableMap<String, Any>) {
        throw NotImplementedError()
    }
}