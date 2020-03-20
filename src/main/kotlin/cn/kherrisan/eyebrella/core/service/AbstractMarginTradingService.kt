package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.core.common.ExchangeStaticConfiguration
import cn.kherrisan.eyebrella.core.common.ServiceDataAdaptor
import cn.kherrisan.eyebrella.core.http.AuthenticationService
import cn.kherrisan.eyebrella.core.http.DefaultSignedHttpService
import cn.kherrisan.eyebrella.core.http.SignedHttpService
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.MarginInfo
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.TransactionResult
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import org.apache.logging.log4j.LogManager
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

abstract class AbstractMarginTradingService(
        val staticConfig: ExchangeStaticConfiguration,
        val dataAdaptor: ServiceDataAdaptor,
        val authenticationService: AuthenticationService)
    : MarginTradingService
        , SignedHttpService by DefaultSignedHttpService(authenticationService)
        , ServiceDataAdaptor by dataAdaptor {

    lateinit var marginMetaInfo: Map<Symbol, MarginInfo>
    val logger = LogManager.getLogger()

    open fun authUrl(path: String): String {
        if (path.startsWith("http")) {
            return path
        }
        return "${staticConfig.marginTradingHttpHost}$path"
    }

    override suspend fun transferToMargin(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun transferToFuture(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    override suspend fun transferToSpot(currency: Currency, amount: BigDecimal, symbol: Symbol): TransactionResult {
        throw NotImplementedError()
    }

    open fun checkResponse(resp: HttpResponse<Buffer>): JsonElement {
        val t = resp.body().toString(StandardCharsets.UTF_8)
        return JsonParser.parseString(t)
    }

    fun jsonObject(resp: HttpResponse<Buffer>): JsonObject {
        val e = checkResponse(resp)
        return e.asJsonObject
    }

    fun jsonArray(resp: HttpResponse<Buffer>): JsonArray {
        val e = checkResponse(resp)
        return e.asJsonArray
    }
}
