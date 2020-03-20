package cn.kherrisan.eyebrella.core.common

interface IHttpUtils {
    fun postBody(vararg pairs: Pair<String, Any?>): MutableMap<String, Any>
    fun getBody(vararg pairs: Pair<String, String?>): MutableMap<String, String>
}

open class HttpUtils : IHttpUtils {

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
}