package cn.kherrisan.eyebrella.exchange.binance

import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.core.common.hmacSHA256Signature
import cn.kherrisan.eyebrella.core.common.urlEncode
import cn.kherrisan.eyebrella.core.http.AuthenticationService
import org.apache.commons.codec.binary.Hex
import org.springframework.stereotype.Component

@Component
class BinanceAuthenticateService : AuthenticationService {

    override fun signedHttpRequest(method: String, path: String, params: MutableMap<String, Any>, headers: MutableMap<String, String>) {
        val apiKey = SpringContainer[BinanceService::class.java].runtimeConfig.apiKey
        val apiSecret = SpringContainer[BinanceService::class.java].runtimeConfig.secretKey
        headers["X-MBX-APIKEY"] = apiKey!!
        params["recvWindow"] = "60000"
        params["timestamp"] = System.currentTimeMillis().toString()
        val payload = urlEncode(params)
        val sigBytes = hmacSHA256Signature(payload, apiSecret!!)
        params["signature"] = Hex.encodeHexString(sigBytes)
    }

    override fun signWebsocketRequest(method: String, path: String, params: MutableMap<String, Any>) {
        throw NotImplementedError()
    }
}