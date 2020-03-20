package cn.kherrisan.eyebrella.core.websocket

import cn.kherrisan.eyebrella.core.http.AuthenticationService
import com.google.gson.JsonElement
import io.vertx.core.Promise

interface AuthenticatableWebsocketDispatcher : WebsocketDispatcher {

    var isAuthenticated: Boolean
    var authenticationPromise: Promise<Any>
    val authenticationService: AuthenticationService

    suspend fun <T : Any> newAuthenticatedSubscription(channel: String, resolver: suspend (JsonElement, DefaultSubscription<T>) -> Unit): Subscription<T> {
        if (!isAuthenticated) {
            authenticate()
        }
        return newSubscription(channel, resolver)
    }

    fun triggerAuthenticationEvent() {
        authenticationPromise.complete()
        isAuthenticated = true
    }

    suspend fun authenticate()


}