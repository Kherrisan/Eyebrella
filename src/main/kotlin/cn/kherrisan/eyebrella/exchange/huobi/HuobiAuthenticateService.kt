package cn.kherrisan.eyebrella.exchange.huobi

import cn.kherrisan.eyebrella.core.common.SpringContainer
import cn.kherrisan.eyebrella.core.common.gmt
import cn.kherrisan.eyebrella.core.common.hmacSHA256Signature
import cn.kherrisan.eyebrella.core.common.sortedUrlEncode
import cn.kherrisan.eyebrella.core.http.AuthenticationService
import java.util.*

class HuobiAuthenticateService(val host: String) : AuthenticationService {

    override fun signedHttpRequest(method: String, path: String, params: MutableMap<String, Any>, headers: MutableMap<String, String>) {
        val apiKey: String = SpringContainer[HuobiRuntimeConfig::class].apiKey!!
        val secretKey: String = SpringContainer[HuobiRuntimeConfig::class].secretKey!!
        params["AccessKeyId"] = apiKey
        params["SignatureMethod"] = "HmacSHA256"
        params["SignatureVersion"] = "2"
        params["Timestamp"] = gmt()
        val sb = StringBuilder(1024)
        sb.append(method.toUpperCase()).append('\n')
                .append("api.huobi.pro").append('\n')
                .append(path.removePrefix(host)).append('\n')
                .append(sortedUrlEncode(params))
        params["Signature"] = Base64.getEncoder().encodeToString(hmacSHA256Signature(sb.toString(), secretKey))
    }

    override fun signWebsocketRequest(method: String, path: String, params: MutableMap<String, Any>) {
        val runtime = SpringContainer[HuobiRuntimeConfig::class]
        params["AccessKeyId"] = runtime.apiKey!!
        params["SignatureVersion"] = "2"
        params["SignatureMethod"] = "HmacSHA256"
        params["Timestamp"] = gmt()
        val sb = StringBuilder(1024)
        sb.append(method).append('\n')
                .append("api.huobi.pro").append('\n')
                .append(path).append('\n')
                .append(sortedUrlEncode(params))
        params["Signature"] = Base64.getEncoder().encodeToString(hmacSHA256Signature(sb.toString(), runtime.secretKey!!))
    }
}