package cn.kherrisan.eyebrella.core.http

/**
 * 身份认证接口
 *
 * 由于各个交易所的很多接口操作需要涉及到用户个人信息，为了保护数据传输的安全性（防篡改、防回放、防伪造），需要使用数字签名来进行身份的验证和数据的保护。
 * 这个接口可以供spotMarket、spotTrading、marginTrading、futureMarket、futureTrading、Account等服务调用，由这些服务的抽象类来继承该接口，
 * 并由具体交易所的实现来来实现签名方法。同一个交易所的不同接口可以使用不同签名算法，同一个接口的不同传输方式（http、ws）也可以使用不同的签名算法。
 *
 */
interface AuthenticationService {

    /**
     * 签名算法
     *
     * @param method String
     * @param path ObjectRef<String>
     * @param params MutableMap<String, Any>?
     * @param headers MutableMap<String, String>?
     */
    fun signedHttpRequest(method: String, path: String, params: MutableMap<String, Any>, headers: MutableMap<String, String>)

    fun signWebsocketRequest(method: String, path: String, params: MutableMap<String, Any>)
}