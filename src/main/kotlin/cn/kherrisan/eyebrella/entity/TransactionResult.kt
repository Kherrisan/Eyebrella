package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open

/**
 * 常用的包含一个操作id的结果
 *
 * 很多api操作都会只返回一个id：普通订单id、借款还款订单id等。
 *
 * @property tid String 操作id
 * @property succeed Boolean 是否成功
 * @property msg String error message
 * @constructor
 */
@Open
data class TransactionResult(val tid: String, val succeed: Boolean = true, val msg: String = "")